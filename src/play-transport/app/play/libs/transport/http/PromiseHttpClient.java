package play.libs.transport.http;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import play.libs.concurrent.Promise;

import java.io.Closeable;

public interface PromiseHttpClient extends Closeable {
   
    public Promise<HttpResponse> execute(HttpUriRequest req);

    public Promise<HttpResponse> execute(HttpHost host, HttpUriRequest req);
    
    public long getLastAccessAt();
    
    public void close();
    
    public Object getRawHttpClient();
    
    public String getName();

    public RequestConfig getDefaultRequestConfig();
}

