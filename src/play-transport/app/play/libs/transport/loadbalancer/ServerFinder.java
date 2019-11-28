package play.libs.transport.loadbalancer;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.http.HttpHost;


import com.google.common.collect.Lists;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.libs.transport.dns.DNSLookup;


public class ServerFinder implements ServerList<Server> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ServerFinder.class);
    private HttpHost target;
    private AtomicReference<List<Server>> servers = new AtomicReference<List<Server>>();

    public ServerFinder(HttpHost target) {
        this.target = target;
    }

    @Override
    public List<Server> getInitialListOfServers() {
        List<Server> servers = getAllByName(target, false); //默认线路DNS
        this.servers.set(servers);
        return servers;
    }

    @Override
    public List<Server> getUpdatedListOfServers() {
        try {
            List<Server> servers = getAllByName(target, false);
            if (servers.size() == 0) {
                servers = getAllByName(target, true);
            }
            if (servers.size() > 0) {
                this.servers.set(servers); //更新服务地址
            }
            return servers;
        } catch (Exception e) {
            LOGGER.error(String.format("%s request dns error", target), e);
        }
        return servers.get();
    }

    public List<Server> getAllByName(final HttpHost target, boolean force) {
        try {
            InetAddress[] adds = DNSLookup.getAllByName(target.getHostName(), force);
            String[] ips = getHostAddress(adds);
            return toServers(ips, target);
        } catch (Exception e) {
            LOGGER.error(String.format("find server by host %s error", target), e);
            return Lists.newArrayList();
        }
    }

    public String[] getHostAddress(InetAddress[] addrs) {
        String[] ips = new String[addrs.length];
        InetAddress addr;
        for (int i = 0; i < addrs.length; i++) {
            addr = addrs[i];
            ips[i] = addr.getHostAddress();
        }
        return ips;
    }

    public List<Server> toServers(String[] ips, HttpHost target) {
        int port = extracPort(target);
        List<Server> servers = new ArrayList<Server>(ips.length + 2);
        Server server;
        for (String ip : ips) {
            server = new Server(ip, port);
            server.setAlive(true);
            servers.add(server);
        }
        return servers;
    }


    public static int extracPort(HttpHost target) {
        if (target.getPort() > 0) {
            return target.getPort();
        }
        if (target.getSchemeName().equalsIgnoreCase("http")) {
            return 80;
        } else if (target.getSchemeName().equalsIgnoreCase("https")) {
            return 443;
        }
        return target.getPort();
    }
}
