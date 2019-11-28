package play.libs.transport.http.client;

import org.apache.http.conn.DnsResolver;
import play.libs.transport.dns.DNS;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutionException;

public class SimpleDNSResover implements DnsResolver {

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        try {
            return DNS.getAllByName(host);
        } catch (ExecutionException e) {
            throw new UnknownHostException(e.getMessage());
        }
    }

}
