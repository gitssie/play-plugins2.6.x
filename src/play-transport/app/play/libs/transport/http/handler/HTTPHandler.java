package play.libs.transport.http.handler;

import org.apache.http.HttpResponse;
import play.libs.transport.http.result.HTTPResult;

public interface HTTPHandler<A> {
    
    public void onCompleted(HttpResponse t, HTTPResult<A> a);
    
    public HTTPResult<A> tryComplete(HttpResponse t);
    
    public void onThrowable(Throwable t);
    
    public scala.concurrent.Promise<A> getPromise();
}

