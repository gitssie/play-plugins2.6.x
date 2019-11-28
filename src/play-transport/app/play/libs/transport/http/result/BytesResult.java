package play.libs.transport.http.result;

public class BytesResult implements HTTPResult<byte[]>{
    private int statusCode;
    private byte[] body;
    private Throwable t;
    
    public BytesResult(int statusCode,byte[] body,Throwable t){
        this.statusCode = statusCode;
        this.body = body;
        this.t = t;
    }
    
    @Override
    public Throwable getThrowable() {
        return t;
    }

    @Override
    public byte[] getBody() {
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

