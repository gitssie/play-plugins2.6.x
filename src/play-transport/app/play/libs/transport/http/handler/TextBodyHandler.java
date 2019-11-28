package play.libs.transport.http.handler;

import akka.dispatch.Futures;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import play.libs.transport.http.HTTPUtils;
import play.libs.transport.http.result.HTTPResult;
import play.libs.transport.http.result.StringResult;
import scala.concurrent.Promise;

public class TextBodyHandler implements HTTPHandler<String>{
    protected final scala.concurrent.Promise<String> promise;

    public TextBodyHandler(scala.concurrent.Promise promise){
        this.promise = promise;
    }

    public <T> TextBodyHandler(){
        this(Futures.promise());
    }
    
    @Override
    public void onCompleted(HttpResponse t, HTTPResult<String> a) {
        this.promise.success(a.getBody());
    }

    @Override
    public HTTPResult<String> tryComplete(HttpResponse resp) {
        try {
            int statusCode = resp.getStatusLine().getStatusCode();
            String rest = EntityUtils.toString(resp.getEntity());
            String body = StringUtils.trim(rest);
            HTTPUtils.closeQuietly(resp);
            return new StringResult(statusCode,body,null);
        } catch (Throwable e) {
            return new StringResult(500,null,e);
        }finally{
            HTTPUtils.closeQuietly(resp);
        }
    }

    @Override
    public void onThrowable(Throwable t) {
        promise.failure(t);
    }

    @Override
    public Promise<String> getPromise() {
        return promise;
    }
}

