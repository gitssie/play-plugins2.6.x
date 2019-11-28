package play.libs.transport.http.result;

import org.apache.http.HttpResponse;

public class RawResult implements HTTPResult<HttpResponse>{
    private int statusCode;
    private HttpResponse body;
    private Throwable t;
    
    public RawResult(int statusCode, HttpResponse body, Throwable t) {
        this.statusCode = statusCode;
        this.body = body;
        this.t = t;
    }

    @Override
    public Throwable getThrowable() {
        return t;
    }

    @Override
    public HttpResponse getBody() {
        return body;
    }

    @Override
    public boolean isThrowable() {
        return t != null;
    }

    @Override
    public boolean isRetry() {
        return t != null;
    }

    @Override
    public int getStatusCode() {
        return statusCode;
    }

}

