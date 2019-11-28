package play.libs.transport.dns;

import java.net.InetAddress;

public class DNSLookup {
    
    public static InetAddress[] getAllByName(String host) throws Exception{
          return getAllByName(host, false);
    }
    public static InetAddress[] getAllByName(String host, boolean force) throws Exception{
          return InetAddress.getAllByName(host);
    }
}
