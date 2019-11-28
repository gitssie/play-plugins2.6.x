package play.libs.transport.http;

import akka.dispatch.Futures;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import play.libs.concurrent.Promise;
import play.libs.transport.hystrix.HystrixCircuitBreaker;

import java.net.URI;

public class AsyncPromiseHttpClient implements PromiseHttpClient{
    private CloseableHttpAsyncClient httpclient;
    private RequestConfig requestConfig;
    private long lastAccessAt;
    private String name;
    private LoadingCache<String,HystrixCircuitBreaker> cache = CacheBuilder.newBuilder()
                        .maximumSize(1 << 10)
                        .build(new CacheLoader<String, HystrixCircuitBreaker>() {
                            @Override
                            public HystrixCircuitBreaker load(String host) throws Exception {
                                return HystrixCircuitBreaker.Factory.create();
                            }
                        });

    public AsyncPromiseHttpClient(CloseableHttpAsyncClient httpclient, String name,RequestConfig requestConfig){
        this.httpclient = httpclient;
        this.name = name;
        this.requestConfig = requestConfig;
    }
    
    public AsyncPromiseHttpClient(CloseableHttpAsyncClient httpclient){
        this(httpclient,null);
    }

    public AsyncPromiseHttpClient(CloseableHttpAsyncClient httpclient,RequestConfig requestConfig){
        this(httpclient,AsyncPromiseHttpClient.class.getSimpleName(),requestConfig);
    }

    @Override
    public Promise<HttpResponse> execute(HttpUriRequest req) {
        return execute(null,req);
    }

    @Override
    public Promise<HttpResponse> execute(HttpHost host, HttpUriRequest req) {
        HystrixCircuitBreaker circuitBreaker = null;
        try {
           this.lastAccessAt = System.currentTimeMillis();

           if(!this.httpclient.isRunning()){
               this.httpclient.start();
           }
           URI uri = req.getURI();
           String tag = host != null ? host.toHostString() : (uri.getHost() + ":" + uri.getPort());
           circuitBreaker = cache.get(tag);
           if(circuitBreaker.allowRequest()) {
               final scala.concurrent.Promise<HttpResponse> xPromise = Futures.promise();
               if(host != null) {
                   httpclient.execute(host, req, new NFutureCallback(circuitBreaker, xPromise));
               }else{
                   httpclient.execute(req, new NFutureCallback(circuitBreaker, xPromise));
               }
               return Promise.wrap(xPromise.future());
           }else{
               Throwable shortCircuitException = new RuntimeException("Hystrix circuit short-circuited and is OPEN");
               HystrixRuntimeException exception = new HystrixRuntimeException(HystrixRuntimeException.FailureType.SHORTCIRCUIT,null,"short-circuited",shortCircuitException,null);
               return Promise.throwing(exception);
           }
       }catch (Throwable e){
           if(circuitBreaker != null){
               circuitBreaker.markFailure();
           }
           return Promise.throwing(e);
       }
    }
    
    @Override
    public void close() {
        IOUtils.closeQuietly(httpclient);
    }
    
    public CloseableHttpAsyncClient getHttpClient(){
        return httpclient;
    }
    
    public static class NFutureCallback implements FutureCallback<HttpResponse> {
        public static final Exception CANCEL_EXCEPTION = new IllegalStateException("Http request cancelled");
        protected final scala.concurrent.Promise<HttpResponse> promise;
        protected final HystrixCircuitBreaker circuitBreaker;
        public NFutureCallback(HystrixCircuitBreaker circuitBreaker,scala.concurrent.Promise<HttpResponse> promise){
            this.promise = promise;
            this.circuitBreaker = circuitBreaker;
        }
        
        @Override
        public void completed(HttpResponse resp) {
            circuitBreaker.markSuccess();
            promise.success(resp);
        }

        @Override
        public void failed(Exception e) {
            circuitBreaker.markFailure();
            promise.failure(e);
        }

        @Override
        public void cancelled() {
            circuitBreaker.markFailure();
            promise.failure(CANCEL_EXCEPTION);
        }
    }

    @Override
    public long getLastAccessAt() {
        return lastAccessAt;
    }

    @Override
    public Object getRawHttpClient() {
        return getHttpClient();
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

