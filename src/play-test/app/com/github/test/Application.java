package com.github.test;


import akka.actor.ActorSystem;
import brave.Tracer;
import com.google.common.collect.Maps;
import controllers.Assets;
import play.Configuration;
import play.Environment;
import play.Logger;
import play.api.assets.AssetsStore;
import play.api.config.Conf;
import play.api.config.ConfValue;
import play.api.config.ConfigManager;
import play.api.ddsl.DdslClient;
import play.api.deadbolt.Authz;
import play.api.deadbolt.Subject;
import play.api.deadbolt.authz.AuthenticationToken;
import play.api.deadbolt.authz.SimpleSubject;
import play.api.deadbolt.authz.UsernamePasswordToken;
import play.api.freemarker.Freemarker;
import play.api.rest.Rest;
import play.api.trace.Traced;
import play.data.DataFactory;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.Json;
import play.libs.concurrent.Futures;
import play.libs.concurrent.HttpExecutionContext;
import play.libs.concurrent.Promise;
import play.libs.ws.WSClient;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import scala.util.Either;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.File;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@Singleton
public class Application extends Controller {
    private Logger.ALogger logger = Logger.of(Application.class);
    @Inject
    private Authz authz;
    @Inject
    private Tracer trace;
    @Inject
    private Futures futures;
    @Inject
    private HttpExecutionContext httpExecutionContext;
    @Inject
    private ActorSystem actorSystem;
    @Inject
    private DdslClient client;
    @Inject
    @Named("conf")
    private Configuration conf;
    @Inject
    private WSClient ws;
    @Inject
    private Freemarker freemarker;
    @Inject
    private Environment env;
    @Inject
    private ConfigManager configManager;
    @Inject
    private DataFactory data;
    @Inject
    private Rest rest;
    private Assets as;
    @Inject
    private AssetsStore assetsStore;
    @Inject
    private ServiceMain serviceMain;

    public Result addConfig(){
        SimpleSubject subject = authz.getSubject(session());

        DynamicForm dForm = data.form().bindFromRequest();
        String key = dForm.get("key");
        String value = dForm.get("value");
        //验证逻辑

        ConfValue confValue = new ConfValue(key,value,"","","",true,new Date());

        configManager.put(key,confValue);
        freemarker.render("hello.html",Maps.newHashMap());

        return ok(env.getFile("index.html")).as(Http.MimeTypes.HTML);
    }



    @Traced
    public Promise<Result> index() throws InterruptedException {

        Http.Headers headers = request().getHeaders();
        headers.toMap();
//        Form<TestForm> dform = data.form(TestForm.class).bindFromRequest();
//        if(dform.hasErrors()){
//            return data.bad(dform);
//        }
//        TestForm testForm = dform.get();

//        ConfValue confValue = new ConfValue("hello","name","text","","我是中文",true,new Date());
//
//        configManager.put("hello",confValue);

        Map<String,Object> map = Maps.newHashMap();
        map.put("hello",serviceMain.sayHello());
        map.put("date",new Date());

        return Promise.pure(map).map(r -> {
            logger.debug("Jack1.....");
            return r;
        }).map(r -> {
            logger.debug("Black2.....");
            return r;
        }).map(m -> {
            logger.debug("Result.....");
            return data.ok(m);
        });

        /**
        final Session session = authz.getSession(session());
        ActorRef actorRef = actorSystem.actorFor("/usr/module/sdadaasda");
        FutureConverters.toJava(Patterns.ask(actorRef,"",1000)).thenApply(resp -> {
            return null;
        });

        String world = (String)session.getAttribute("hello");
        if(world == null) {
            world = "world";
            session.setAttribute("hello", world);
        }else{
            world = "--> OLD WORD" + world;
        }
        /**
        Map<String,Object> map = Maps.newHashMap();
        map.put("hello","我是Java");
        map.put("date",new Date());
        map.put("bool", BigDecimal.valueOf(0.023423));

        scala.concurrent.Promise<Result> p = FutureConverters.promise();
        p.success(ok("i'm ok"));

        HttpGet get = new HttpGet("http://www.baidu.com");
        return HTTP.doText(trace,get).map((t) -> {
            return ok(Html.apply(t));
        },httpExecutionContext.current());**/
    }

    public Result login(String username,String password){

        AuthenticationToken token = new UsernamePasswordToken(username,password);

        Either<Subject,String> e = authz.login(token,session());

        return ok(Json.toJson(e.left().get()));
    }

    public CompletionStage<Result> md5(){
        String url = "https://open.weixin.qq.com/sns/explorer_broker";//?appid=wx2f5d8f9715c59d10&redirect_uri=http://plogin.m.jd.com&scope=snsapi_base&ua=wechat&from_safari=0";

        String ua = "Mozilla/5.0 (Linux; U; Android 5.1; zh-cn; m1 metal Build/LMY47I) AppleWebKit/537.36 (KHTML, like Gecko)Version/4.0 Chrome/37.0.0.0 MQQBrowser/7.6 Mobile Safari/537.36";//MicroMessenger/6.3.25.861";
        return ws.url(url)
                .addQueryParameter("appid","wx2f5d8f9715c59d10")
                .addQueryParameter("redirect_uri","http://wqs.jd.com")
                .addQueryParameter("scope","snsapi_base")
                .addHeader("User-Agent",ua).execute().thenApply((resp) -> {

            return ok(resp.getBody());//.as(Http.MimeTypes.HTML);
        });

    }
}
