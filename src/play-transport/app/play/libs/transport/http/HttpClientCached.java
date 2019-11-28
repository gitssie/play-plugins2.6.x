package play.libs.transport.http;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import org.apache.commons.io.IOUtils;
import scala.concurrent.duration.Duration;

import java.util.concurrent.TimeUnit;

public class HttpClientCached {
    public static final Duration IDLE_TIME = Duration.apply("10 minutes"); //10分钟
    private Cache<String, PromiseHttpClient> httpClients = CacheBuilder.newBuilder()
                                    .maximumSize(100)
                                    .expireAfterAccess(10,TimeUnit.MINUTES)
                                    .removalListener(new RemovalListener<String, PromiseHttpClient>() {
                                        @Override
                                        public void onRemoval(RemovalNotification<String,PromiseHttpClient> event) {
                                            IOUtils.closeQuietly(event.getValue());
                                        }
                                    }).build();
    private String cachedName;

    public HttpClientCached(){
        this("HTTP_CLIENT_DEFAULT_CACHED");
    }
    
    public HttpClientCached(String cachedName){
        this.cachedName = cachedName;
    }
    
    
    public PromiseHttpClient getHttpClient(String key){
        return httpClients.getIfPresent(key);
    }
    
    public void putHttpClient(String key,PromiseHttpClient httpClient){
        this.httpClients.put(key, httpClient);
    }

    @Override
    public String toString() {
        return "HttpClientCached [cachedName=" + cachedName + "]";
    }

}

