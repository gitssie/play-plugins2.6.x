package play.libs.transport.http;

import brave.Tracer;
import com.netflix.loadbalancer.ILoadBalancer;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import play.libs.concurrent.Promise;
import play.libs.transport.http.client.HttpClientFactory;
import play.libs.transport.http.handler.BytesBodyHandler;
import play.libs.transport.http.handler.HTTPHandler;
import play.libs.transport.http.handler.RawBodyHandler;
import play.libs.transport.http.handler.TextBodyHandler;
import play.libs.transport.loadbalancer.DefaultLoadBalancerRetryHandler;
import play.libs.transport.loadbalancer.HostLoadBalancer;
import play.libs.transport.loadbalancer.RetryHandler;

import java.util.concurrent.CompletableFuture;

public class HTTP {
    public static final RetryHandler DEFAULT_RETRY = new DefaultLoadBalancerRetryHandler(0,1,true);//重复1次

    public static Promise<byte[]> doBytes(final Tracer tracer, final HttpUriRequest req) {
        return doBytes(tracer,req, HttpClientFactory.getHttpClient());
    }

    public static Promise<byte[]> doBytes(final Tracer tracer, final HttpUriRequest req, PromiseHttpClient client){
        HTTPInvokerContext context = new HTTPInvokerContext(client);
        context.setRetryHandler(DEFAULT_RETRY);
        return makeHTTP(tracer,req, new BytesBodyHandler(), context);
    }

    public static Promise<HttpResponse> doRaw(final Tracer tracer,final HttpUriRequest req) {
        return doRaw(tracer,req,HttpClientFactory.getHttpClient());
    }
    public static Promise<HttpResponse> doRaw(final Tracer tracer,final HttpUriRequest req, PromiseHttpClient client){
        HTTPInvokerContext context = new HTTPInvokerContext(client);
        context.setRetryHandler(DEFAULT_RETRY);
        return makeHTTP(tracer,req, new RawBodyHandler(), context);
    }

    public static <A> Promise<String> doText(final Tracer tracer,final HttpUriRequest req) {
        return doText(tracer,req,HttpClientFactory.getHttpClient());
    }

    public static <A> Promise<String> doText(final Tracer tracer,final HttpUriRequest req, PromiseHttpClient client){
        HTTPInvokerContext context = new HTTPInvokerContext(client);
        context.setRetryHandler(DEFAULT_RETRY);
        return makeHTTP(tracer,req, new TextBodyHandler(), context);
    }
    
    public static Promise<byte[]> doBytes(final Tracer tracer,final HttpUriRequest req, PromiseHttpClient client, RetryHandler retryHandler){
        HTTPInvokerContext context = new HTTPInvokerContext(client);
        context.setRetryHandler(retryHandler);
        return makeHTTP(tracer,req, new BytesBodyHandler(), context);
    }
    
    public static Promise<HttpResponse> doRaw(final Tracer tracer,final HttpUriRequest req, PromiseHttpClient client, RetryHandler retryHandler){
        HTTPInvokerContext context = new HTTPInvokerContext(client);
        context.setRetryHandler(retryHandler);
        return makeHTTP(tracer,req, new RawBodyHandler(), context);
    }
    
    public static <A> Promise<String> doText(final Tracer tracer,final HttpUriRequest req, PromiseHttpClient client, RetryHandler retryHandler){
        HTTPInvokerContext context = new HTTPInvokerContext(client);
        context.setRetryHandler(retryHandler);
        return makeHTTP(tracer,req, new TextBodyHandler(), context);
    }
    
    public static <A> Promise<A> makeHTTP(final Tracer tracer,final HttpUriRequest req, final HTTPHandler<A> handler, PromiseHttpClient client, RetryHandler retryHandler){
        HTTPInvokerContext context = new HTTPInvokerContext(client);
        context.setRetryHandler(retryHandler);
        return makeHTTP(tracer,req, handler, context);
    }
    
    public static <A> Promise<A> makeHTTP(final Tracer tracer,final HttpUriRequest req, final HTTPHandler<A> handler, PromiseHttpClient client){
        HTTPInvokerContext context = new HTTPInvokerContext(client);
        return makeHTTP(tracer,req, handler, context);
    }
    
    public static <A> Promise<A> makeHTTP(final Tracer tracer,final HttpUriRequest req, final HTTPHandler<A> handler, final HTTPInvokerContext context){
        final scala.concurrent.Promise<A> xPromise = handler.getPromise();
        final Promise<A> promise = Promise.wrap(xPromise.future());
        try{
            final ILoadBalancer loadBalancer = context.getLoadBalancer() == null ? HostLoadBalancer.getLoadBalancer(req.getURI()) : context.getLoadBalancer();
            final HTTPInvoker invoke = HTTPInvoker.builder()
                    .withHttpClient(context.getHttpClient())
                    .withLoadBalancer(loadBalancer)
                    .withRetryHandler(context.getRetryHandler())
                    .withHttpRequest(req)
                    .withHttpHandler(handler)
                    .withExecutionContext(context.getExecutionContext())
                    .withTracer(tracer)
                    .build();
            invoke.run();
        }catch (Throwable e){
            xPromise.failure(e);
        }
        return promise;
    }
}

