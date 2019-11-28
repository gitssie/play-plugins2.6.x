package play.libs.transport.dns;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * JVM为了提高效率，会将第一次的DNS结果缓存起来。而且你不重新启动JVM缓存永远不失效。
 所以你的服务器一旦有了DNS更新的话，你只能重新启动服务来重新更新缓存。那么有没有什么办法可以设置一个缓存失效时间呢？JVM提供了两个启动参数：
 设置解析成功的域名记录JVM中缓存的有效时间，这里修改为10秒钟有效，0表示禁止缓存，-1表示永远有效
 -Dsun.net.inetaddr.ttl=10
 设置解析失败的域名记录JVM中缓存的有效时间，这里修改为10秒钟有效，0表示禁止缓存，-1表示永远有效
 -Dsun.net.inetaddr.negative.ttl=10
 */
public class DNS {
    private static Logger LOGGER = LoggerFactory.getLogger(DNS.class);
    private static final LoadingCache<String, InetAddress[]> ADDRS_CACHE = CacheBuilder.newBuilder()
                                .expireAfterWrite(10, TimeUnit.SECONDS)
                                .maximumSize(1000)
                                .build(new CacheLoader<String, InetAddress[]>() {
                                    @Override
                                    public InetAddress[] load(String host) throws Exception {
                                        try{
                                            return DNSLookup.getAllByName(host);
                                        }catch (Exception e){
                                            LOGGER.error("load ip address by host["+host+"] error",e);
                                            throw e;
                                        }
                                    }
                                });

    public static InetAddress[] getAllByName(String host) throws ExecutionException {
        return ADDRS_CACHE.get(host);
    }
}
