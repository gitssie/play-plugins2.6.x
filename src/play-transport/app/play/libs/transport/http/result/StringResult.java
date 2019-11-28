package play.libs.transport.http.result;

public class StringResult implements HTTPResult<String>{
    private int statusCode;
    private String body;
    private Throwable t;
    
    public StringResult(int statusCode,String body, Throwable t) {
        this.statusCode = statusCode;
        this.body = body;
        this.t = t;
    }

    @Override
    public Throwable getThrowable() {
        return t;
    }

    @Override
    public String getBody() {
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

