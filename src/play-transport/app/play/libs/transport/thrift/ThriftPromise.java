package play.libs.transport.thrift;

import org.apache.thrift.async.AsyncMethodCallback;

import akka.dispatch.Futures;
import play.libs.concurrent.Promise;

public class ThriftPromise<A> implements AsyncMethodCallback<A> {
    private scala.concurrent.Promise<A> promise = Futures.promise();
    
    @Override
    public void onComplete(A result) {
        promise.success(result);
    }

    @Override
    public void onError(Exception t) {
        promise.failure(t);
    }

    public Promise<A> getPromise(){
        return Promise.wrap(promise.future());
    }

}