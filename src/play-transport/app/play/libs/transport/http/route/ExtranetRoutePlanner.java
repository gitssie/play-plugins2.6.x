package play.libs.transport.http.route;

import com.google.common.collect.Lists;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.protocol.HttpContext;
import sun.net.util.IPAddressUtil;

import java.util.List;

public class ExtranetRoutePlanner extends DefaultProxyRoutePlanner {
    private List<SubnetUtils.SubnetInfo> subnetInfos;

    public ExtranetRoutePlanner(final HttpHost proxy,List<String> proxyIgnoreMask) {
        super(proxy);
        if(proxyIgnoreMask != null && proxyIgnoreMask.size() > 0){//代理忽略网段
            subnetInfos = Lists.newArrayList();
            for(String p : proxyIgnoreMask){
                SubnetUtils subnetUtils = new SubnetUtils(p);
                subnetInfos.add(subnetUtils.getInfo());
            }
        }
    }

    @Override
    protected HttpHost determineProxy(
            final HttpHost target,
            final HttpRequest request,
            final HttpContext context) throws HttpException {

        //IP ADDRESS
        if(subnetInfos != null && subnetInfos.size() > 0 && IPAddressUtil.textToNumericFormatV4(target.getHostName()) != null){
            String ipAddr = target.getHostName();
            for(SubnetUtils.SubnetInfo info : subnetInfos){
                if(info.isInRange(ipAddr)){//在代理忽略网段
                    return null; //无需代理
                }
            }
        }
        return super.determineProxy(target,request,context);
    }
}
