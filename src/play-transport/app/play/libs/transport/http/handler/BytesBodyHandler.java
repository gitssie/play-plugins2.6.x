package play.libs.transport.http.handler;

import akka.dispatch.Futures;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import play.libs.transport.http.HTTPUtils;
import play.libs.transport.http.result.BytesResult;
import play.libs.transport.http.result.HTTPResult;
import scala.concurrent.Promise;

public class BytesBodyHandler implements HTTPHandler<byte[]>{
    protected final scala.concurrent.Promise<byte[]> promise;

    public BytesBodyHandler(scala.concurrent.Promise<byte[]> promise){
        this.promise = promise;
    }

    public <T> BytesBodyHandler(){
        this(Futures.promise());
    }
    
    @Override
    public void onCompleted(HttpResponse t, HTTPResult<byte[]> a) {
        promise.success(a.getBody());
    }

    @Override
    public HTTPResult<byte[]> tryComplete(HttpResponse resp) {
        try {
            int statusCode = resp.getStatusLine().getStatusCode();
            byte[] body = EntityUtils.toByteArray(resp.getEntity());
            HTTPUtils.closeQuietly(resp);
            return new BytesResult(statusCode,body,null);
        } catch (Throwable e) {
            return new BytesResult(500,null,e);
        }finally{
            HTTPUtils.closeQuietly(resp);
        }
    }

    @Override
    public void onThrowable(Throwable t) {
        promise.failure(t);
    }

    @Override
    public Promise<byte[]> getPromise() {
        return promise;
    }
}

