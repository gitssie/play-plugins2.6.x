package play.libs.transport.http.result;

public interface HTTPResult<A>{
    
    public Throwable getThrowable();
    
    public A getBody();
    
    public int getStatusCode();
    
    public boolean isThrowable();
    
    public boolean isRetry();
}

