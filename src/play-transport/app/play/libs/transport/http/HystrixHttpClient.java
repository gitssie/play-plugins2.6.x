package play.libs.transport.http;

import akka.dispatch.Futures;
import com.netflix.hystrix.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import play.Configuration;
import play.libs.concurrent.Promise;
import rx.Observer;

import java.net.URI;

public class HystrixHttpClient implements PromiseHttpClient {
    private CloseableHttpClient httpclient;
    private RequestConfig requestConfig;
    private long lastAccessAt;
    private String name;
    private int threadPoolSize;
    private int queueSize;

    public HystrixHttpClient(CloseableHttpClient httpclient, String name, Configuration conf,RequestConfig requestConfig){
        this.httpclient = httpclient;
        this.name = name;
        threadPoolSize = conf.getInt("ws.hystrix.poolsize",100);
        queueSize = conf.getInt("ws.hystrix.queuesize",10000);
        this.requestConfig = requestConfig;
    }

    public HystrixHttpClient(CloseableHttpClient httpclient,Configuration conf){
        this(httpclient,conf,null);
    }

    public HystrixHttpClient(CloseableHttpClient httpclient,Configuration conf,RequestConfig requestConfig){
        this(httpclient,HystrixHttpClient.class.getName(),conf,requestConfig);
    }

    @Override
    public Promise<HttpResponse> execute(HttpUriRequest req) {
        return execute(null,req);
    }

    @Override
    public Promise<HttpResponse> execute(HttpHost host, HttpUriRequest req) {
        lastAccessAt = System.currentTimeMillis();
        final scala.concurrent.Promise<HttpResponse> promise = Futures.promise();
        HystrixCmd cmd = new HystrixCmd(httpclient,host,req,this);
        cmd.observe().subscribe(new Observer<HttpResponse>() {
            @Override
            public void onCompleted() {}
            @Override
            public void onError(Throwable throwable) {
                promise.failure(throwable);
            }
            @Override
            public void onNext(HttpResponse o) {
                promise.success(o);
            }
        });
        return Promise.wrap(promise.future());
}

    @Override
    public long getLastAccessAt() {
        return lastAccessAt;
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(httpclient);
    }

    public int getThreadPoolSize(){
        return threadPoolSize;
    }

    public int getQueueSize(){
        return queueSize;
    }

    public String getCommandGroup(HttpHost host, HttpUriRequest req){
        return "HystrixHttpClient";
    }

    public String getThreadKey(HttpHost host, HttpUriRequest req){
        return "HystrixHttpClient";
    }

    public String getCommandKey(HttpHost host, HttpUriRequest req){
        URI uri = req.getURI();
        return uri.getHost() + ":" + uri.getPort();
    }

    public int getTimeoutInMilliseconds(HttpUriRequest req){
        if(req instanceof HttpRequestBase){
            HttpRequestBase base = (HttpRequestBase) req;
            return base.getConfig().getConnectionRequestTimeout() + base.getConfig().getConnectTimeout() + base.getConfig().getSocketTimeout();
        }else{
            return 10 * 10000;
        }
    }

    public static class HystrixCmd extends HystrixCommand<HttpResponse> {
        private CloseableHttpClient httpClient;
        private HttpHost host;
        private HttpUriRequest req;

        public HystrixCmd(CloseableHttpClient httpClient, HttpUriRequest req, HystrixHttpClient hhc) {
            this(httpClient, null, req,hhc);
        }
        
        public HystrixCmd(CloseableHttpClient httpClient, HttpHost host, HttpUriRequest req, HystrixHttpClient hhc) {
            super(Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(hhc.getCommandGroup(host,req)))
                    .andCommandKey(HystrixCommandKey.Factory.asKey(hhc.getCommandKey(host,req)))
                    .andThreadPoolKey(HystrixThreadPoolKey.Factory.asKey(hhc.getThreadKey(host,req)))
                    .andCommandPropertiesDefaults(HystrixCommandProperties.Setter().withExecutionIsolationThreadTimeoutInMilliseconds(hhc.getTimeoutInMilliseconds(req)))
                    .andThreadPoolPropertiesDefaults(HystrixThreadPoolProperties.Setter().withCoreSize(hhc.getThreadPoolSize()).withMaxQueueSize(hhc.getQueueSize())));

            this.httpClient = httpClient;
            this.host = host;
            this.req = req;
        }

        @Override
        protected HttpResponse run() throws Exception {
            if(host != null){
                return httpClient.execute(host,req);
            }else{
                return httpClient.execute(req);
            }
        }
    }

    @Override
    public Object getRawHttpClient() {
        return httpclient;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public RequestConfig getDefaultRequestConfig() {
        return requestConfig;
    }
}
