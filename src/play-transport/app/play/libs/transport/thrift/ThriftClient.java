package play.libs.transport.thrift;

import brave.Tracer;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.thrift.TBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.Configuration;
import play.api.trace.TraceService;
import play.libs.F.Tuple;
import play.libs.concurrent.Promise;
import play.libs.transport.http.HTTP;
import play.libs.transport.http.HTTPInvokerContext;
import play.libs.transport.http.PromiseHttpClient;
import play.libs.transport.http.client.HttpClientFactory;
import play.libs.transport.http.client.IOType;
import play.libs.transport.http.handler.HTTPHandler;
import play.libs.transport.loadbalancer.DefaultLoadBalancerRetryHandler;
import play.libs.transport.loadbalancer.HostLoadBalancer;
import play.libs.transport.loadbalancer.RetryHandler;
import play.libs.transport.loadbalancer.TelnetPing;
import scala.concurrent.ExecutionContext;
import scala.concurrent.impl.ExecutionContextImpl;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ThriftClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThriftClient.class);
    private static final int DEFAULT_DIAL_TIME = 5 * 1000;  //5连接超时
    private static final int DEFAULT_READ_TIME = 30 * 1000; //30秒超时

    private HTTPInvokerContext httpContext;
    private Builder build;
    public ThriftClient(Builder build){
        this.build = build;
        this.httpContext = createHttpContext(build);
    }
    
    public RequestConfig createReqConfig(Builder build) {
        RequestConfig.Builder builder = RequestConfig.custom();
        builder.setCircularRedirectsAllowed(false)
                .setRedirectsEnabled(false)
                .setConnectTimeout(build.dialTimeout)
                .setSocketTimeout(build.readTimeout);
        return builder.build();
    }
    
    @SuppressWarnings("rawtypes")
    public <A extends TBase> Tuple<HttpUriRequest,String> makeHttpRequest(String requrl, A req, RequestConfig reqConfig) {
        String reqStr = req.toString();
        HttpPost httpPost = new HttpPost(requrl);
        if(reqConfig != null) httpPost.setConfig(reqConfig);
        ByteArrayEntity entityParams = new ByteArrayEntity(Thrifts.toBytes(req));
        httpPost.setEntity(entityParams);
        return new Tuple<HttpUriRequest,String>(httpPost,reqStr);
    }
    
    /**
     * 创建HTTP的重试机制
     * @author 尹有松
     * @return
     */
    public RetryHandler createRetryHandler(int retryCount){
        if(retryCount <= 0){
            return RetryHandler.DEFAULT;
        }else{
            return new DefaultLoadBalancerRetryHandler(0,retryCount,true);
        }
    }
    
    public HTTPInvokerContext createHttpContext(Builder build){
        PromiseHttpClient client = HttpClientFactory.createHttpClient(IOType.ASYNC_NIO);
        HTTPInvokerContext httpContext = new HTTPInvokerContext(client);
        httpContext.setLoadBalancer(build.loadBalancer);
        httpContext.setRetryHandler(createRetryHandler(build.retryCount));
        httpContext.setExecutionContext(build.executionContext);
        return httpContext;
    }

    public <A extends TBase,B extends TBase> Promise<B> request(final String requrl,
                                                                final A req,
                                                                final Class<B> clazz) {
        return request(TraceService.getTracer(),requrl,req,clazz);
    }
    /**
     * 执行标准POST请求
     * @author 尹有松
     * @param requrl
     * @param req
     * @param clazz
     * @return
     */
    @SuppressWarnings("rawtypes")
    public <A extends TBase,B extends TBase> Promise<B> request(final Tracer tracer,final String requrl,
                                                                final A req,
                                                                final Class<B> clazz) {
        
        RequestConfig reqConfig = createReqConfig(this.build);
        String url = build.host + build.httpPrefix + requrl;
        Tuple<HttpUriRequest,String> httpReq = makeHttpRequest(url, req, reqConfig);
        
        //准备执行请求
        LOGGER.debug("{} with params:{}",httpReq._1,httpReq._2);
        try {
            final ThriftBodyHandler<B> handler = new ThriftBodyHandler<B>(clazz);
            //创建HTTPContext
            final HTTPInvokerContext context = this.httpContext;
            //执行HTTP请求
            return HTTP.makeHTTP(tracer,httpReq._1, handler, context);
        } catch (Exception e) {
            LOGGER.error("prepare thrift http invoke error",e);
            return Promise.throwing(e);
        }
    }

    public <A> scala.concurrent.Promise<A> execute(final String path,byte[] req, HTTPHandler<A> handler) {
        return execute(path,new ByteArrayEntity(req),handler);
    }
    public <A> scala.concurrent.Promise<A> execute(final String path,HttpEntity httpEntity, HTTPHandler<A> handler) {
        Tracer tracer = TraceService.getTracer();
        RequestConfig reqConfig = createReqConfig(this.build);
        String url = build.host + build.httpPrefix + path;
        HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(reqConfig);
        httpPost.setEntity(httpEntity);
        try {
            //创建HTTPContext
            final HTTPInvokerContext context = this.httpContext;
            //执行HTTP请求
            HTTP.makeHTTP(tracer,httpPost, handler, context);
        } catch (Throwable e) {
            LOGGER.error("prepare thrift http invoke error",e);
            handler.onThrowable(e);
        }
        return handler.getPromise();
    }

    public static Builder builder(){
        return new Builder();
    }
    
    public static Builder builder(Configuration config){
        Builder build = new Builder();
        build.withLoadBalancer(config.getString("servers"))
             .withHost(config.getString("host",""))
             .withHttpPrefix(config.getString("prefix",""))
             .withDialTimeout(config.getMilliseconds("dialTimeout",(long)DEFAULT_DIAL_TIME).intValue())
             .withReadTimeout(config.getMilliseconds("readTimeout",(long)DEFAULT_READ_TIME).intValue())
             .withRetryCount(config.getInt("retryCount",0));
        
        int executor = config.getInt("executor",0);
        if(executor > 0){
            Executor stage = Executors.newFixedThreadPool(executor);
            build.withExecutionContext(new ExecutionContextImpl(stage,null));
        }
        return build;
    }
    
    public static class Builder {
        int dialTimeout = DEFAULT_DIAL_TIME;
        int readTimeout = DEFAULT_READ_TIME;
        int retryCount = 3;
        ILoadBalancer loadBalancer;
        ExecutionContext executionContext;
        String host = "";
        String httpPrefix = "";
        
        public ThriftClient build(){
            return new ThriftClient(this);
        }
        
        public Builder withDialTimeout(int dialTimeout){
            this.dialTimeout = dialTimeout;
            return this;
        }
        public Builder withReadTimeout(int readTimeout){
            this.readTimeout = readTimeout;
            return this;
        }
        public Builder withRetryCount(int retryCount){
            this.retryCount = retryCount;
            return this;
        }
        public Builder withHttpPrefix(String httpPrefix){
            this.httpPrefix = httpPrefix;
            return this;
        }
        public Builder withLoadBalancer(ILoadBalancer loadBalancer){
            this.loadBalancer = loadBalancer;
            return this;
        }
        public Builder withHost(String host){
            this.host = host;
            return this;
        }
        public Builder withExecutionContext(ExecutionContext executionContext){
            this.executionContext = executionContext;
            return this;
        }
        public Builder withLoadBalancer(String serverList){
            List<Server> servers = HostLoadBalancer.parseServerList(serverList);
            BaseLoadBalancer loadBalancer = LoadBalancerBuilder.newBuilder()
                                    .buildFixedServerListLoadBalancer(servers);
            loadBalancer.setPing(new TelnetPing());
            this.loadBalancer = loadBalancer;
            return this;
        }
        public Builder withLoadBalancer(BaseLoadBalancer loadBalancer){
            this.loadBalancer = loadBalancer;
            return this;
        }
    }
}

