package play.libs.transport.http.result;

public class PureResult<A> implements HTTPResult<A>{
    private int statusCode;
    private A body;
    private Throwable t;
    private Boolean needRetry;
    
    public PureResult(int statusCode,A body, Throwable t,Boolean needRetry) {
        this.statusCode = statusCode;
        this.body = body;
        this.t = t;
        this.needRetry = needRetry;
    }

    public PureResult(int statusCode,A body, Throwable t) {
        this(statusCode, body, t, null);
    }
    
    @Override
    public Throwable getThrowable() {
        return t;
    }

    @Override
    public A getBody() {
        return body;
    }

    @Override
    public boolean isThrowable() {
        return t != null;
    }

    @Override
    public boolean isRetry() {
        if(needRetry == null){
            if(t != null) return true;
            return false;
        }else{
            return needRetry;
        }
    }

    @Override
    public int getStatusCode() {
        return statusCode;
    }

}

