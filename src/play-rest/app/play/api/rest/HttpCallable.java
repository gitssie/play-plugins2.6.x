package play.api.rest;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import play.libs.concurrent.Promise;
import play.libs.transport.http.HTTP;
import play.libs.transport.http.HTTPInvokerContext;
import play.libs.transport.http.HTTPUtils;
import play.libs.transport.http.handler.RawBodyHandler;

import java.io.IOException;
import java.util.concurrent.Callable;

public class HttpCallable<T> implements Callable<Promise<T>> {
    private final Rest rest;
    private final ServiceMethod<T, ?> serviceMethod;
    private final Object[] args;

    public HttpCallable(Rest rest,ServiceMethod<T, ?> serviceMethod, Object[] args) {
        this.rest = rest;
        this.serviceMethod = serviceMethod;
        this.args =args;
    }

    @Override
    public Promise<T> call() throws Exception {
        HttpUriRequest req = serviceMethod.toCall(args);
        HTTPInvokerContext context = new HTTPInvokerContext(rest.callFactory());
        context.setExecutionContext(rest.getCallbackExecutor());

        Promise<HttpResponse> result = HTTP.makeHTTP(rest.tracer(),req,new RawBodyHandler(),context);
        return result.map((resp) -> {
            int code = resp.getStatusLine().getStatusCode();
            if (code < 200 || code >= 300) {
                try {
                    // Buffer the entire body to avoid future I/O.
                    //ResponseBody bufferedBody = Utils.buffer(rawBody);
                    throw new HttpResponseException(code,HTTPUtils.toString(resp.getEntity()));
                } finally {
                    HTTPUtils.closeQuietly(resp);
                }
            }
            try {
                if (code == 204 || code == 205) {
                    HTTPUtils.closeQuietly(resp);
                    return null;
                }
                return serviceMethod.toResponse(resp);
            } catch (IOException e) {
                HTTPUtils.closeQuietly(resp);
                throw new RuntimeException(e);
            }
        });
    }

}
