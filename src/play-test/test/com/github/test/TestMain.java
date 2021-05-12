package com.github.test;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import ognl.Ognl;
import ognl.OgnlContext;
import ognl.OgnlException;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.junit.Test;
import play.Configuration;
import play.api.rest.Rest;
import play.api.rest.converter.jackson.JacksonConverterFactory;
import play.api.rest.http.*;
import play.data.OneResult;
import play.libs.Json;
import play.libs.concurrent.Promise;
import play.libs.transport.http.HTTPUtils;
import play.libs.transport.http.PromiseHttpClient;
import play.libs.transport.http.client.HttpClientFactory;
import play.libs.transport.http.client.IOType;
import scala.concurrent.Await;
import scala.concurrent.duration.Duration;
import sun.misc.IOUtils;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;


public class TestMain {

    private Rest rest = new Rest.Builder()
            .baseUrl("http://172.17.20.231:20606")
            //.requestConfig(RequestConfig.custom().setSocketTimeout(60 * 1000).build())
            .addConverterFactory(JacksonConverterFactory.create())
            .build();

    @Test
    public void testSubnet(){
        SubnetUtils utils = new SubnetUtils("172.17.20.0/9");

        System.out.println(utils.getInfo());
    }

    @Test
    public void testSendMail() throws Exception {
        Map<String,String> params = Maps.newHashMap();
        params.put("toEmail","jessie@ulopay.com");
        params.put("subject","这是一封邮件的标题");
        params.put("body","这是一封邮件的内容，可以支持HTML<h1>变大</h1>");

        SFTPApi api = rest.create(SFTPApi.class);
        Promise<?> p = api.post("/email/send",params).map(r -> {
            System.out.println(r);
            return "";
        });
        Await.result(p.asScala(), Duration.apply("3000s"));
    }



    @Test
    public void testProxy() throws Exception {
        Map<String,Object> params = Maps.newHashMap();
        params.put("ws.http.proxyIgnore", Lists.newArrayList("172.17.20.0/24"));
        params.put("ws.http.proxyHost","172.17.20.231");
        params.put("ws.http.proxyPort","20605");

        Config config = ConfigFactory.parseMap(params);
        Configuration conf = new Configuration(config);

        PromiseHttpClient client = HttpClientFactory.createHttpClient(IOType.SYNC_IO,conf);

        HttpUriRequest req = new HttpGet("http://172.17.20.231:20680/#/overview/list");
        HttpHost host = new HttpHost("172.17.20.231",20680);

        Promise<?> p = client.execute(host,req).map(r -> {
            String body = HTTPUtils.toString(r.getEntity());
            System.out.println(body);
            HTTPUtils.closeQuietly(r);
            return null;
        });
        Await.result(p.asScala(), Duration.apply("3000s"));
    }


    @Test
    public void testOgn() throws OgnlException {
        OgnlContext ctx = new OgnlContext();
        ctx.put("x",400);
        Number n = (Number) Ognl.getValue("(x >0 && x <=100) ? 3 : (x >100 && x <=200) ? 4 : (x >200 && x <=300) ? 5 : (x*0.021)",ctx);
        Double d = n.doubleValue();
        System.out.println(d);

    }

    @Test
    public void testSftpDownload() throws Exception {
        SFTPApi api = rest.create(SFTPApi.class);
        Map<String,String> params = Maps.newHashMap();
        params.put("hostname","sftp.95516.com");
        params.put("username","agentpay1234");
        params.put("password","agent2018");
        params.put("source","/GLNFSAPS/FLNFS/04375852/20181022/IND18102232ACOMA");
        Promise<?> p = api.downSftp("http://172.17.20.231:20606/sftp/downlaod",params).map(r -> {
            Header h = r.getFirstHeader("Content-Type");
            if(h.getValue().equals("application/json")) {
                System.out.println(h);
            }
            String body = HTTPUtils.toString(r.getEntity());
            System.out.println(body);
            HTTPUtils.closeQuietly(r);
            return null;
        });

        Await.result(p.asScala(), Duration.apply("3000s"));
    }

    @Test
    public void testDownBill() throws Exception {
        HttpPost req = new HttpPost("https://api.mch.weixin.qq.com/pay/downloadbill");
        req.addHeader("Accept-Encoding","");
        req.setEntity(new StringEntity("<xml><appid><![CDATA[wxcccc8abc4b42abab]]></appid>\n" +
                "<bill_date><![CDATA[20180902]]></bill_date>\n" +
                "<bill_type><![CDATA[ALL]]></bill_type>\n" +
                "<mch_id><![CDATA[1433998702]]></mch_id>\n" +
                "<nonce_str><![CDATA[1535953011]]></nonce_str>\n" +
                "<sign><![CDATA[0250831f744875d75d4f1b65055c90a8]]></sign>\n" +
                "</xml>"));

        Promise<?> p = HttpClientFactory.createHttpClient(IOType.SYNC_IO).execute(req).map((r) -> {
            try{
                System.out.println(r.getFirstHeader("Content-Length").getValue());
                InputStream in = r.getEntity().getContent();
                String root = "/Users/jessie/Workspace/子商户信息/";
                byte[] b = new byte[1024];
                int len;
                OutputStream out = new FileOutputStream(new File(root,"1433998702.csv"));
                while((len = in.read(b)) > 0){
                    out.write(b,0,len);
                }
                org.apache.commons.io.IOUtils.closeQuietly(out);
            }catch (Exception e){
                e.printStackTrace();
            }
            return r;
        });
        Await.result(p.asScala(), Duration.apply("3000s"));
    }

    @Test
    public void testIndexForm() throws Exception {
        AuthActionRest api = rest.create(AuthActionRest.class);
        TestForm.InnerForm innerForm = new TestForm.InnerForm();
        innerForm.setHello(null);

        TestForm form = new TestForm();
        form.setId(3);
        form.setHello("333");
        form.setInnerForm(innerForm);

        Promise<?> p = api.index(form).map((r) -> {
            if(r.isSuccess()){
                System.out.println("login success");
                System.out.println("data:"+r.getData());
            }else{
                System.out.println("login failure");
                System.out.println("message:" + r.getMessage());
            }
            return r;
        });

        Await.result(p.asScala(), Duration.apply("10s"));
    }

    public static interface AuthActionRest{
        @POST("/admin/index")
        public Promise<OneResult> index(@Body TestForm testForm);

    }

    private String readExternalizedNodeValue(String raw) {
        raw = raw.replaceAll("\\n", "\n");
        return  raw;
    }

    @Test
    public void testJson() throws IOException{
        String file = "/Users/jessie/Downloads/代付参数.txt";
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while(reader.ready()){
            line = reader.readLine();
            if(line.matches("/.+=.+=.*")) {
                int firstEq = line.indexOf('=');
                int secEq = line.indexOf('=', firstEq + 1);

                String path = line.substring(0, firstEq);
                if ("/".equals(path)) {
                    path = "";
                }
                String name = line.substring(firstEq + 1, secEq);

                String value = readExternalizedNodeValue(line.substring(secEq + 1));
                Json.parse(value);
                if(name.equals("SEARCH_RANGE")){
                    Map mp = Json.fromJson(Json.parse(value),Map.class);
                    Object x = Json.parse((String)mp.get("value"));
                    System.out.println(x);
                }
            }
        }

    }


    @Test
    public void testApp() throws IOException{
        String x = "202210000000000001519";
        System.out.println(x);
        System.out.println(Long.valueOf(x));
    }

    public Map<String,String> toMaps1() throws IOException{
        String root = "/Users/jessie/Workspace/子商户信息/";
        List<String> lines = Files.readLines(new File(root+"137464353124110088.csv"), Charset.defaultCharset());
        Map<String,String> map = Maps.newHashMap();

        for(String line : lines){
            String[] parts = line.split(",");
            if(parts.length > 8){
                map.put(parts[8].replace("`",""),line);
            }
        }

        return map;

    }

    public Map<String,String> toMaps2() throws IOException{
        String root = "/Users/jessie/Workspace/子商户信息/";
        List<String> lines = Files.readLines(new File(root+"26384463_20180520.csv"), Charset.defaultCharset());
        List<String> lines2 = Files.readLines(new File(root+"26384462_20180520.csv"), Charset.defaultCharset());
        lines.addAll(lines2);

        Map<String,String> map = Maps.newHashMap();

        for(String line : lines){
            String[] parts = line.split(",");
            if(parts.length > 4){
                map.put(parts[4].replace("`",""),line);
            }
        }

        return map;

    }

    @Test
    public void testURL() throws Exception{
        String x = "s3:http://045c1ae70c6e2196bc34:5cf5c13f587db1b7ff1d449c7c6c9602947410e3@172.17.20.231:20628/?style=path";
        URI url = new URI(x);

        System.out.println(url);
    }

    interface SFTPApi{
        @Streaming
        @POST
        Promise<HttpResponse> downSftp(@Url String url,@Body Map<String,String> params);

        @POST
        Promise<String> post(@Url String url,@Body Map<String,String> params);
    }
}
