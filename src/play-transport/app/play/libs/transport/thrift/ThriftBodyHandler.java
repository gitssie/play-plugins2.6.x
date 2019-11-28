package play.libs.transport.thrift;

import akka.dispatch.Futures;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TProtocolException;
import play.libs.transport.http.handler.HTTPHandler;
import play.libs.transport.http.result.HTTPResult;
import play.libs.transport.http.result.PureResult;
import scala.concurrent.Promise;

import java.io.Closeable;


public class ThriftBodyHandler<A extends TBase> implements HTTPHandler<A> {
    protected final scala.concurrent.Promise<A> promise = Futures.promise();
    private Class<A> clazz;
    
    public ThriftBodyHandler(Class<A> clazz){
        this.clazz = clazz;
    }
    
    @Override
    public void onCompleted(HttpResponse t, HTTPResult<A> a) {
        promise.success(a.getBody());
    }

    @Override
    public HTTPResult<A> tryComplete(HttpResponse resp) {
        //协议方面的问题,都不需要重试
        try {
            int statusCode = resp.getStatusLine().getStatusCode();
            if(statusCode == 200){
                byte[] body = EntityUtils.toByteArray(resp.getEntity());
                try {
                    A inst = Thrifts.parseForm(clazz, body); //解析Thrift实体
                    HTTPResult<A> result = new PureResult<A>(statusCode, inst, null,false);
                    return result;
                } catch (Exception e) { //解析协议错误
                    String strBody = new String(body);
                    TException th = new TProtocolException(TProtocolException.INVALID_DATA,strBody,e);
                    HTTPResult<A> result = new PureResult<A>(500,null,th,false);
                    return result;
                }
            }else{
                //错误的返回结果
//                String body = EntityUtils.toString(resp.getEntity());
                TException th = new TProtocolException(TProtocolException.INVALID_DATA,resp.getStatusLine().toString());
                HTTPResult<A> result = new PureResult<A>(statusCode,null, th,false);
                return result;
            }
        } catch (Exception e) {
            HTTPResult<A> result = new PureResult<A>(500,null, e,false);
            return result;
        }finally{
            if(resp instanceof Closeable){
                IOUtils.closeQuietly((Closeable)resp);
            }
        }
    }

    @Override
    public void onThrowable(Throwable t) {
        promise.failure(t);
    }

    @Override
    public Promise<A> getPromise() {
        return promise;
    }
}

