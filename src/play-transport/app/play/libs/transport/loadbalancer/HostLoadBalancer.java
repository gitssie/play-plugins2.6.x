package play.libs.transport.loadbalancer;

import com.google.common.cache.*;
import com.google.common.collect.Lists;
import com.netflix.loadbalancer.*;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.utils.URIUtils;
import play.Configuration;
import play.libs.transport.http.client.HttpClientFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HostLoadBalancer {

    private static LoadingCache<HttpHost,ILoadBalancer> HOST_LB = CacheBuilder.newBuilder()
                        .maximumSize(1000)
                        .expireAfterAccess(10, TimeUnit.MINUTES)
                        .removalListener(new RemovalListener<HttpHost, ILoadBalancer>() {
                            @Override
                            public void onRemoval(RemovalNotification<HttpHost, ILoadBalancer> removalNotification) {
                                ILoadBalancer lb = removalNotification.getValue();
                                if(lb instanceof BaseLoadBalancer){
                                    ((BaseLoadBalancer) lb).shutdown();
                                }
                            }
                        })
                        .build(new CacheLoader<HttpHost, ILoadBalancer>() {
                            @Override
                            public ILoadBalancer load(HttpHost host) throws Exception {
                                return getLoadBalancerInner(host);
                            }
                        });

    public static ILoadBalancer getLoadBalancer(URI uri) throws Exception {
        HttpHost target = null;
        target = URIUtils.extractHost(uri);
        if (target == null) {
            throw new IllegalStateException("URI does not specify a valid host name: " + uri);
        }
        ILoadBalancer lb = getLoadBalancer(target);
        return lb;
    }

    public static ILoadBalancer getLoadBalancer(final HttpHost target) throws Exception {
        return HOST_LB.get(target);
    }

    public static ILoadBalancer getLoadBalancerInner(final HttpHost target) throws Exception {
        Configuration conf = HttpClientFactory.getConfiguration();
        boolean isEnabled = conf.getBoolean("ws.http.enabledLoadBalancer",true);
        if(isEnabled) {
           return LoadBalancerBuilder.newBuilder()
                    .withDynamicServerList(new ServerFinder(target))
                    .withRule(new RoundRobinRule())
                    .buildDynamicServerListLoadBalancer();
        }else{
            return new NoOpLoadBalancer();
        }
    }
    
    public static List<Server> parseServerList(String serverList){
        String[] sl = serverList.split(",");
        List<Server> servers = Lists.newArrayListWithCapacity(sl.length * 2);
        String[] sp;
        Server ser;
        for(String s : sl){
            sp = s.split(":");
            if(sp.length == 2){
                ser = new Server(sp[0], NumberUtils.toInt(sp[1], 80));
                ser.setAlive(true);
                servers.add(ser);
            }
        }
        return servers;
    }
}
