package play.libs.transport.http.handler;

import akka.dispatch.Futures;
import org.apache.http.HttpResponse;
import play.libs.transport.http.result.HTTPResult;
import play.libs.transport.http.result.RawResult;
import scala.concurrent.Promise;

public class RawBodyHandler implements HTTPHandler<HttpResponse>{
    
    protected final scala.concurrent.Promise<HttpResponse> promise = Futures.promise();
    
    @Override
    public void onCompleted(HttpResponse t, HTTPResult<HttpResponse> a) {
        promise.success(a.getBody());
    }

    @Override
    public HTTPResult<HttpResponse> tryComplete(HttpResponse t) {
        int statusCode = t.getStatusLine().getStatusCode();
        return new RawResult(statusCode,t, null);
    }

    @Override
    public void onThrowable(Throwable t) {
        promise.failure(t);
    }

    @Override
    public Promise<HttpResponse> getPromise() {
        return promise;
    }
}

