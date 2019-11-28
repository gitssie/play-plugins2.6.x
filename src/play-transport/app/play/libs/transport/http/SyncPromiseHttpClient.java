package play.libs.transport.http;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import play.libs.concurrent.Promise;

public class SyncPromiseHttpClient implements PromiseHttpClient{
    private CloseableHttpClient httpclient;
    private RequestConfig requestConfig;
    private long lastAccessAt;
    private String name;
    
    public SyncPromiseHttpClient(CloseableHttpClient httpclient, String name, RequestConfig requestConfig){
        this.httpclient = httpclient;
        this.name = name;
        this.requestConfig = requestConfig;
    }
    
    public SyncPromiseHttpClient(CloseableHttpClient httpclient){
        this(httpclient,null);
    }

    public SyncPromiseHttpClient(CloseableHttpClient httpclient,RequestConfig requestConfig){
        this(httpclient, SyncPromiseHttpClient.class.getName(),requestConfig);
    }
    @Override
    public Promise<HttpResponse> execute(HttpUriRequest req){
        this.lastAccessAt = System.currentTimeMillis();
        try {
            HttpResponse response = httpclient.execute(req); //请求量大,这里阻塞等死
            return Promise.pure(response);
        } catch (Exception e) {
            return Promise.throwing(e);
        }
    }

    @Override
    public Promise<HttpResponse> execute(HttpHost host, HttpUriRequest req){
        this.lastAccessAt = System.currentTimeMillis();
        try {
            HttpResponse response = httpclient.execute(host,req); //请求量大,这里阻塞等死
            return Promise.pure(response);
        } catch (Exception e) {
            return Promise.throwing(e);
        }
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(httpclient);
    }

    public CloseableHttpClient getHttpClient(){
        return httpclient;
    }
    
    @Override
    public long getLastAccessAt() {
        return lastAccessAt;
    }

    @Override
    public Object getRawHttpClient() {
        return getHttpClient();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public RequestConfig getDefaultRequestConfig() {
        return requestConfig;
    }
}

