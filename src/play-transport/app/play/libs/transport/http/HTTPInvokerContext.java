package play.libs.transport.http;

import com.netflix.loadbalancer.ILoadBalancer;
import play.libs.transport.loadbalancer.RetryHandler;
import scala.concurrent.ExecutionContext;

public class HTTPInvokerContext {
    private RetryHandler retryHandler;
    private ILoadBalancer loadBalancer;
    private PromiseHttpClient   httpClient;
    private ExecutionContext executionContext;
    
    public HTTPInvokerContext(){}
    
    public HTTPInvokerContext(PromiseHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public RetryHandler getRetryHandler() {
        return retryHandler;
    }
    public void setRetryHandler(RetryHandler retryHandler) {
        this.retryHandler = retryHandler;
    }
    public ILoadBalancer getLoadBalancer() {
        return loadBalancer;
    }
    public void setLoadBalancer(ILoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }
    public PromiseHttpClient getHttpClient() {
        return httpClient;
    }
    public void setHttpClient(PromiseHttpClient httpClient) {
        this.httpClient = httpClient;
    }
    public ExecutionContext getExecutionContext() {
        return executionContext;
    }
    public void setExecutionContext(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }
    public HTTPInvokerContext copy(){
        HTTPInvokerContext context = new HTTPInvokerContext();
        context.httpClient = this.httpClient;
        context.retryHandler = this.retryHandler;
        context.loadBalancer = this.loadBalancer;
        return context;
    }
}

