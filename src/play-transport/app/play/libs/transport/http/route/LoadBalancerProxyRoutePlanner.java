package play.libs.transport.http.route;

import java.util.List;
import java.util.Map;

import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.protocol.HttpContext;

import com.google.common.collect.Maps;
import com.netflix.loadbalancer.BaseLoadBalancer;
import com.netflix.loadbalancer.ILoadBalancer;
import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.Server;
import play.libs.transport.loadbalancer.TelnetPing;


public class LoadBalancerProxyRoutePlanner extends DefaultProxyRoutePlanner {
    protected ILoadBalancer loadBalancer;
    protected Map<Server, HttpHost> hosts = Maps.newHashMap();
    
    public LoadBalancerProxyRoutePlanner(List<Server> servers,SchemePortResolver schemePortResolver) {
        super(new HttpHost("127.0.0.1", 0), schemePortResolver);
        loadBalancer = createLLoadBalancer(servers);
    }

    protected ILoadBalancer createLLoadBalancer(List<Server> servers){
        BaseLoadBalancer loadBalancer = LoadBalancerBuilder.newBuilder()
            .buildFixedServerListLoadBalancer(servers);
        loadBalancer.setPing(new TelnetPing());
        return loadBalancer;
    }
    
    protected HttpHost determineProxy(HttpHost target, HttpRequest request, HttpContext context)
        throws HttpException {
        Server server = loadBalancer.chooseServer(null);
        HttpHost proxy = hosts.get(server);
        if(proxy == null && server != null){
            proxy = new HttpHost(server.getHost(),server.getPort());
            synchronized (this) {
                hosts.put(server,proxy);
            }
        }
        return proxy;
    }
}
