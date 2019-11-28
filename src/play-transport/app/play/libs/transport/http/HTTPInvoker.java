package play.libs.transport.http;

import brave.Span;
import brave.Tracer;
import brave.internal.HexCodec;
import brave.propagation.TraceContext;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerContext;
import com.netflix.loadbalancer.Server;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.api.libs.Execution;
import play.libs.concurrent.Promise;
import play.libs.transport.http.client.HttpClientFactory;
import play.libs.transport.http.handler.HTTPHandler;
import play.libs.transport.http.result.HTTPResult;
import play.libs.transport.loadbalancer.DefaultLoadBalancerRetryHandler;
import play.libs.transport.loadbalancer.RetryHandler;
import scala.concurrent.ExecutionContext;

import java.net.ConnectException;
import java.net.URI;
import java.util.function.Consumer;

public class HTTPInvoker implements Runnable{
    public static Logger LOGGER = LoggerFactory.getLogger(HTTPInvoker.class);
    private final HttpUriRequest request;
    private final HTTPHandler handler;
    private RetryHandler retryHandler = RetryHandler.DEFAULT;
    private final PromiseHttpClient httpClient;
    private final ILoadBalancer loadBalancer;
    private final LoadBalancerContext loadBalancerContext;
    private final Server server;
    private final ExecutionContext executionContext;
    private final ExecutionInfoContext context = new ExecutionInfoContext();
    private final Tracer tracer;
    private long startAt = 0;
    private int timeout = -1;

    private HTTPInvoker(Builder builder){
        this.request = builder.request;
        this.handler  = builder.handler;
        this.retryHandler = builder.retryHandler;
        this.httpClient = builder.httpClient;
        this.server = builder.server;
        this.loadBalancer = builder.loadBalancer;
        this.loadBalancerContext = builder.loadBalancerContext;
        this.executionContext = builder.executionContext  == null ? Execution.httpPromiseContext() : builder.executionContext;
        this.tracer = builder.tracer;

        RequestConfig requestConfig = null;
        if(request instanceof HttpRequestBase){
            requestConfig = ((HttpRequestBase) request).getConfig();
        }
        requestConfig = requestConfig !=null ? requestConfig : httpClient.getDefaultRequestConfig();
        if(requestConfig != null){
            int maxTimeout = requestConfig.getConnectTimeout() + requestConfig.getConnectionRequestTimeout() + requestConfig.getSocketTimeout();
            if(maxTimeout > 0){
                this.timeout = maxTimeout;
            }
        }
        if(this.retryHandler == null){
            this.retryHandler = new DefaultLoadBalancerRetryHandler(HttpClientFactory.getConfiguration());
        }
    }
    
    @Override
    public void run() {
        submit();
    }
    
    public boolean retryPolicy(final boolean same,final int maxRetrys,Throwable e){
        int currentRetry = same ? context.attemptCount : context.getServerAttemptCount();
        if (currentRetry > maxRetrys) { //超过重复次数
            return false;
        }
        boolean isRetry = retryHandler.isRetriableException(e, same) || retryHandler.isCircuitTrippingException(e);
        if(isRetry && timeout > 0 && (System.currentTimeMillis() - startAt) < timeout){
            return true;
        }else if(timeout <= 0){
            return isRetry;
        }else{
            return false;
        }
    }
    
    public void submit(){
        request(false);
    }
    
    public void request(final boolean same){
        if(startAt == 0){
            startAt = System.currentTimeMillis();
        }
        try {
            //获取服务
            Server server = this.server == null ? (same ? context.getServer() : selectServer()) : this.server;
            if(!same){
                context.setServer(server); //设置服务器
            }
            context.incAttemptCount();
            makeInvoke(server,request,handler);
        } catch (Exception e) {
            LOGGER.error("retry make http invoke error",e);
            handler.onThrowable(e);
        }
    }
    
    public RetryHandler getRetryHandler(){
        return retryHandler;
    }
    
    public Server selectServer() throws Exception {
        if(this.loadBalancerContext != null){
            return this.loadBalancer.chooseServer(null);
            //return this.loadBalancerContext.getServerFromLoadBalancer(null, null);
        }
        return null;
    }

    private HttpHost determineTarget(HttpUriRequest request) throws IllegalStateException {
        HttpHost target = null;
        URI requestURI = request.getURI();
        if (requestURI.isAbsolute()) {
            target = URIUtils.extractHost(requestURI);
            if (target == null) {
                throw new IllegalStateException("URI does not specify a valid host name: " + requestURI);
            }
        }
        return target;
    }
    public void makeInvoke(Server server, HttpUriRequest request, HTTPHandler handler){
        try {
            HttpHost host = determineTarget(request);
            if(server != null) host = new HttpHost(server.getHost(),server.getPort(),host.getSchemeName());

            Span span = tracer.nextSpan().name("HTTPInvoker.invoke").kind(Span.Kind.CLIENT).start();
            span.tag("server",server == null ? host.getHostName() : server.getHostPort());
            span.tag("attempt_count",context.getAttemptCount() + "");
            String reqUri = request.getURI().toString();

            if(LOGGER.isInfoEnabled()) {
                LOGGER.info(request.toString());
            }

            propagationTraceInfo(request, span);

            Promise<HttpResponse> promise = httpClient.execute(host,request);
            promise.onRedeem(new SuccessCallback(span,handler,this,retryHandler), Promise.toExecutor(executionContext));
            promise.onFailure(new FailureCallback(reqUri,span,handler,this,retryHandler),Promise.toExecutor(executionContext));

        } catch (Throwable e) {
            handler.onThrowable(e);
            LOGGER.error("{} http error,with request:{},server:{}",httpClient.getName(),request,server);
        }
    }

    public void propagationTraceInfo(HttpUriRequest request, Span span) {
        TraceContext currentSpan = span.context();
        request.addHeader("X-B3-TraceId",currentSpan.traceIdString());
        request.addHeader("X-B3-SpanId", HexCodec.toLowerHex(currentSpan.spanId()));
        if(currentSpan.parentId() != null) {
            request.addHeader("X-B3-ParentSpanId", HexCodec.toLowerHex(currentSpan.parentId()));
        }
        request.addHeader("X-B3-Sampled",String.valueOf(currentSpan.sampled()));
    }

    static class SuccessCallback implements Consumer<HttpResponse> {
        private HTTPHandler handler;
        private HTTPInvoker invoke;
        private RetryHandler retryHandler;
        private Span span;
        private static final Throwable FAIL_EXCEPTION = new ConnectException("Invalid result");
        
        public SuccessCallback(Span span,HTTPHandler handler,HTTPInvoker invoke,RetryHandler retryHandler){
            this.span = span;
            this.handler = handler;
            this.invoke = invoke;
            this.retryHandler = retryHandler;
        }
        @Override
        public void accept(HttpResponse a) {
            HTTPResult<?> success = handler.tryComplete(a);//有流读取,存在堵塞的情况
            span.tag("status_code",success.getStatusCode() + "");
            span.finish();

            if(success.isRetry()){ //需要重试
                final int maxRetrysSame = retryHandler.getMaxRetriesOnSameServer();
                final int maxRetrysNext = retryHandler.getMaxRetriesOnNextServer();
                
                if(invoke.retryPolicy(maxRetrysSame > 0,maxRetrysSame,FAIL_EXCEPTION)){ //先试上一台机器
                    invoke.request(true);
                }else if(invoke.retryPolicy(false,maxRetrysNext,FAIL_EXCEPTION)){//试下一台机器
                    invoke.request(false);
                }else{ //重试完了所有的机器,还是业务错误
                    if(success.isThrowable()){
                        handler.onThrowable(success.getThrowable()); //重试时报错,异常
                    }else{
                        handler.onCompleted(a,success); //执行完成,但是还是业务错误
                    }
                }
            }else{//不需要重试
                if(success.isThrowable()){ //有异常
                    handler.onThrowable(success.getThrowable()); //重试时报错,异常
                }else{
                    handler.onCompleted(a,success); //执行完成
                }
            }
        }
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder{
        private RetryHandler        retryHandler;
        private ILoadBalancer loadBalancer;
        private LoadBalancerContext loadBalancerContext;
        private Server server;
        private PromiseHttpClient   httpClient;
        private HttpUriRequest request;
        private HTTPHandler         handler;
        private ExecutionContext executionContext;
        private Tracer tracer;
        private Builder() {}
    
        public Builder withExecutionContext(ExecutionContext executionContext) {
            this.executionContext = executionContext;
            return this;
        }
        
        public Builder withLoadBalancer(ILoadBalancer loadBalancer) {
            this.loadBalancer = loadBalancer;
            return this;
        }
 
        public Builder withRetryHandler(RetryHandler retryHandler) {
            this.retryHandler = retryHandler;
            return this;
        }
        
        public Builder withHttpClient(PromiseHttpClient  httpClient) {
            this.httpClient = httpClient;
            return this;
        }
        
        public Builder withHttpRequest(HttpUriRequest request) {
            this.request = request;
            return this;
        }
        
        public Builder withHttpHandler(HTTPHandler handler) {
            this.handler = handler;
            return this;
        }
        
        public Builder withServer(Server server) {
            this.server = server;
            return this;
        }

        public Builder withTracer(Tracer tracer){
            this.tracer = tracer;
            return this;
        }

        public HTTPInvoker build() {
            if (httpClient == null) {
                throw new IllegalArgumentException("httpClient needs to be set");
            }
            
            if (request == null) {
                throw new IllegalArgumentException("request needs to be set");
            }
            
            if (handler == null) {
                throw new IllegalArgumentException("handler needs to be set");
            }
            if (loadBalancerContext == null && loadBalancer != null) {
                loadBalancerContext = new LoadBalancerContext(loadBalancer);
            }
            return new HTTPInvoker(this);
        }
    }
    
    static class FailureCallback implements Consumer<Throwable>{
        private HTTPHandler handler;
        private HTTPInvoker invoke;
        private RetryHandler retryHandler;
        private Span span;
        private String reqUri;

        public FailureCallback(String reqUri,Span span,HTTPHandler handler,HTTPInvoker invoke,RetryHandler retryHandler){
            this.reqUri = reqUri;
            this.span = span;
            this.handler = handler;
            this.invoke = invoke;
            this.retryHandler = retryHandler;
        }
        @Override
        public void accept(Throwable e) {
            final int maxRetrysSame = retryHandler.getMaxRetriesOnSameServer();
            final int maxRetrysNext = retryHandler.getMaxRetriesOnNextServer();

            span.tag("error",e.getClass().getSimpleName() + ":" + e.getMessage());
            span.finish();

            LOGGER.error("{},request times:{},exception:{}",this.reqUri,invoke.context.getServerAttemptCount(),e);
            
            if(invoke.retryPolicy(maxRetrysSame > 0,maxRetrysSame,e)){ //先试上一台机器
                invoke.request(true);
            }else if(invoke.retryPolicy(false,maxRetrysNext,e)){//试下一台机器
                invoke.request(false);
            }else{////重试完了所有的机器,还是网络错误
                handler.onThrowable(e);
            }
        }
    }
    
    public static class ExecutionInfoContext {
        Server server;
        int         serverAttemptCount = 0;
        int         attemptCount = 0;
        
        public void setServer(Server server) {
            this.server = server;
            this.serverAttemptCount++;
        }
        
        public void incAttemptCount() {
            this.attemptCount++;
        }
        
        public int getAttemptCount() {
            return attemptCount;
        }

        public Server getServer() {
            return server;
        }

        public int getServerAttemptCount() {
            return this.serverAttemptCount;
        }
    }
    
}

