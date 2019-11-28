package play.api.sysguard.action;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.LoggerFactory;
import play.Configuration;
import play.Logger;
import play.api.deadbolt.Authz;
import play.api.deadbolt.Subject;
import play.api.deadbolt.authz.AuthenticationToken;
import play.api.deadbolt.authz.UsernamePasswordToken;
import play.api.libs.changestream.actors.EsProxy;
import play.data.Form;
import play.data.FormFactory;
import play.libs.Json;
import play.libs.concurrent.HttpExecutionContext;
import play.libs.crypto.CookieSigner;
import play.libs.ws.*;
import play.mvc.*;
import scala.util.Either;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

@Singleton
public class SysGuardAction extends Controller implements DefaultBodyWritables{
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(SysGuardAction.class);
    private Configuration conf;
    private WSClient ws;
    private HttpExecutionContext ec;
    private Authz authz;
    private FormFactory formFactory;
    private Map<String,Function<Http.Request,CompletionStage<Result>>> locations;
    private EsProxy esProxy;
    private CookieSigner cookieSigner;

    @Inject
    public SysGuardAction(Configuration conf, WSClient ws, HttpExecutionContext ec, Authz authz, FormFactory formFactory, CookieSigner cookieSigner){
        this.conf = conf;
        this.ws = ws;
        this.ec = ec;
        this.authz = authz;
        this.formFactory = formFactory;
        this.esProxy = new EsProxy(conf.underlying(),ws);
        this.cookieSigner = cookieSigner;
        buildProxy(conf);
    }

    private void buildProxy(Configuration conf) {
        List<Configuration> proxyConf = conf.getConfigList("play.sysguard.proxy.location");

        locations = Maps.newHashMap();

        for(Configuration cf : proxyConf){
            String path = cf.getString("path");
            String proxyPass = cf.getString("proxy_pass");
            boolean transparent = cf.getBoolean("transparent",true);
            locations.put(path,(req) -> {
                return proxyRequest(req,path,proxyPass,transparent);
            });
        }
    }

    public CompletionStage<Result> sysguard(String path){
        Http.Request req = request();
        String uri = req.uri();
        for(Map.Entry<String,Function<Http.Request,CompletionStage<Result>>> entry : locations.entrySet()){
            String pt = entry.getKey();
            if(uri.startsWith(pt)){
                return entry.getValue().apply(req);
            }
        }
        return CompletableFuture.completedFuture(badRequest("Not Found Location To Proxy Pass"));
    }

    public CompletionStage<Result> proxyRequest(Http.Request req,String path,String proxyPass,boolean transparent){
        Http.Headers headers = req.getHeaders();
        String reqUri = req.path();
        if(!transparent){
            reqUri = reqUri.substring(path.length(),reqUri.length());
        }
        reqUri = proxyPass + reqUri;

        WSRequest wsReq = ws.url(reqUri);

        for(Map.Entry<String,String[]> entry : req.queryString().entrySet()){
            for(String v : entry.getValue()){
                wsReq = wsReq.addQueryParameter(entry.getKey(),v);
            }
        }

        for(Map.Entry<String,List<String>> header: headers.toMap().entrySet()){
            String key = header.getKey();
            for(String h : header.getValue()){
                if(key.equalsIgnoreCase(CONNECTION) || key.equalsIgnoreCase(HOST)){
                    break;
                }
                wsReq = wsReq.addHeader(header.getKey(),h);
            }
        }

        String m = req.method();
        CompletionStage<WSResponse> resp = null;
        if(m.equalsIgnoreCase("POST")) {
            resp = wsReq.post(new InMemoryBodyWritable(req.body().asBytes(), headers.get(CONTENT_TYPE).get()));
        }else if(m.equalsIgnoreCase("PUT")) {
            resp = wsReq.put(new InMemoryBodyWritable(req.body().asBytes(), headers.get(CONTENT_TYPE).get()));
        }else if(m.equalsIgnoreCase("GET")){
            resp = wsReq.get();
        }else if(m.equalsIgnoreCase("DELETE")){
            resp = wsReq.delete();
        }else if(m.equalsIgnoreCase("HEAD")){
            resp = wsReq.head();
        }else{
            return CompletableFuture.completedFuture(badRequest("Unsupported Http Method To Proxy Pass"));
        }
        return resp.thenApplyAsync((rp) -> {
            for(Map.Entry<String,List<String>> entry : rp.getHeaders().entrySet()){
                for(String v : entry.getValue()){
                    if(entry.getKey().equalsIgnoreCase(CONTENT_TYPE) || entry.getKey().equalsIgnoreCase(CONTENT_LENGTH)){
                        continue;
                    }
                    response().setHeader(entry.getKey(),v);
                }
            }
            return status(rp.getStatus(),rp.getBodyAsBytes()).as(rp.getContentType());
        },ec.current());
    }

    public Result login(){
        Form<UserForm> form = formFactory.form(UserForm.class).bindFromRequest();
        if(form.hasErrors()){
            ObjectNode node = (ObjectNode)form.errorsAsJson();
            node.put("status",400);
            return ok(node);
        }
        UserForm loginForm = form.get();

        AuthenticationToken token = new UsernamePasswordToken(loginForm.getEmail(),loginForm.getPassword());

        Either<Subject,String> e = authz.login(token,session());
        if(e.isRight()){
            ObjectNode node = Json.newObject();
            node.put("status",401);
            node.put("message",e.right().get());
            return ok(node);
        }else{
            ObjectNode node = Json.newObject();
            node.put("status",200);
            node.put("message","success");
            return ok(node);
        }
    }

    public Result sendMsg(){
        ObjectNode onode = (ObjectNode)request().body().asJson();
        String nonce = String.valueOf(System.currentTimeMillis());
        String curTime = String.valueOf((new Date()).getTime() / 1000L);
        String tagStr = conf.getString("play.sms.netease.appsercet") + nonce + curTime;
        String checkSum = DigestUtils.sha1Hex(tagStr);
        String url = conf.getString("play.sms.netease.emplate.send");
        String time = FastDateFormat.getInstance("HH:mm").format(new Date());
        Map<String,String> parasm = Maps.newHashMap();
        parasm.put("templateid",conf.getString("play.sms.templateid"));
        parasm.put("mobiles",Json.toJson(conf.getString("play.sms.phones").split(",")).toString());
        parasm.put("params",Json.toJson(Lists.newArrayList("监控",time + onode.get("title").asText())).toString());
        ws.url(url)
            .addHeader("AppKey", conf.getString("play.sms.netease.appkey"))
            .addHeader("Nonce", nonce)
            .addHeader("CurTime", curTime)
            .addHeader("CheckSum", checkSum)
            .post(body(parasm))
            .thenAcceptAsync((r) ->{
                Logger.info("send sms status:{},msg:{}",r.getStatus(),r.getBody());
        },ec.current());

        return ok();
    }

    /**
     * 这里接收的内容比较大,存储到临时文件里面去了
     * @return
     */
    @BodyParser.Of(BodyParser.Raw.class)
    public CompletionStage<Result> esBulk() {
        Http.Request req = request();
        Optional<String> hmac = req.getHeaders().get("X-HMAC-SIGN");
        String body = new String(req.body().asRaw().asBytes(1 << 20).toArray());
        String mymac = cookieSigner.sign(body);
        if(!(hmac.isPresent() && mymac.equals(hmac.get()))){
            return CompletableFuture.completedFuture(badRequest("sign not match"));
        }
        return esProxy.bulkRow(body).thenApplyAsync((rp) -> {
            for(Map.Entry<String,List<String>> entry : rp.getHeaders().entrySet()){
                for(String v : entry.getValue()){
                    if(entry.getKey().equalsIgnoreCase(CONTENT_TYPE) || entry.getKey().equalsIgnoreCase(CONTENT_LENGTH)){
                        continue;
                    }
                    response().setHeader(entry.getKey(),v);
                }
            }
            return status(rp.getStatus(),rp.getBodyAsBytes()).as(rp.getContentType());
        },ec.current());
    }

}
