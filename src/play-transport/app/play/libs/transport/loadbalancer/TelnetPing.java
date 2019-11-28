package play.libs.transport.loadbalancer;

import com.netflix.loadbalancer.IPing;
import com.netflix.loadbalancer.Server;
import org.apache.commons.net.telnet.TelnetClient;

import java.io.IOException;

public class TelnetPing implements IPing {

    @Override
    public boolean isAlive(Server server) {
        if(server == null || !server.isReadyToServe()) return false;
        TelnetClient client = new TelnetClient();
        client.setConnectTimeout(3 * 1000); //3秒连接超时
        boolean connected = false;
        try {
            client.connect(server.getHost(),server.getPort());
            connected = true;
        }catch (IOException e) {
           return false;
        }finally{
            if(connected){
                try {
                    client.disconnect();
                } catch (IOException e) {}
            }
        }
        return true;
    }
}

