package play.libs.transport.http;

import com.netflix.hystrix.exception.HystrixRuntimeException;
import play.libs.transport.loadbalancer.DefaultLoadBalancerRetryHandler;

public class HttpRetryHandler extends DefaultLoadBalancerRetryHandler {
    public HttpRetryHandler() {
        super();
    }

    public HttpRetryHandler(int retrySameServer, int retryNextServer, boolean retryEnabled) {
        super(retrySameServer,retryNextServer,retryEnabled);
        addCircuitRelatedException(HystrixRuntimeException.class);//临时错误,需要重试
    }


    public void addRetriableException(Class<? extends Throwable> thr){
        getRetriableExceptions().add(thr);
    }

    public void addCircuitRelatedException(Class<? extends Throwable> thr){
        getCircuitRelatedExceptions().add(thr);
    }

}
