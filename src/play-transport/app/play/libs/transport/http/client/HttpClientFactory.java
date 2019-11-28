package play.libs.transport.http.client;

import com.netflix.loadbalancer.Server;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.DefaultConnectionReuseStrategy;
import org.apache.http.impl.NoConnectionReuseStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.conn.NoopIOSessionStrategy;
import org.apache.http.nio.conn.SchemeIOSessionStrategy;
import org.apache.http.nio.conn.ssl.SSLIOSessionStrategy;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.apache.http.protocol.HttpContext;
import play.Configuration;
import play.Play;
import play.libs.transport.http.AsyncPromiseHttpClient;
import play.libs.transport.http.HystrixHttpClient;
import play.libs.transport.http.PromiseHttpClient;
import play.libs.transport.http.SyncPromiseHttpClient;
import play.libs.transport.http.route.ExtranetRoutePlanner;
import play.libs.transport.http.route.LoadBalancerProxyRoutePlanner;
import play.libs.transport.loadbalancer.HostLoadBalancer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.util.List;
import java.util.Properties;
import java.util.function.Function;

public class HttpClientFactory {
    public static final IOType DEFAULT_IO_TYPE = IOType.ASYNC_NIO;
    public static final PromiseHttpClient HTTP_CLIENT = HttpClientFactory.createHttpClient(DEFAULT_IO_TYPE);
    
    public static PromiseHttpClient getHttpClient(){
        return HTTP_CLIENT;
    }
    
    public static PromiseHttpClient createHttpClient(IOType type){
        return createHttpClient(type, null,null);
    }
    
    public static PromiseHttpClient createHttpClient(IOType type, Configuration config){
        return createHttpClient(type, null,config);
    }
    
    public static PromiseHttpClient createHttpClient(IOType type,SSLContext sslcontext){
        return createHttpClient(type, sslcontext,null);
    }
    
    public static PromiseHttpClient createHttpClient(IOType type,String wsName){
        return createHttpClient(type, null, null, wsName);
    }
    
    
    public static PromiseHttpClient createHttpClient(IOType type, SSLContext sslcontext, Configuration config){
        return createHttpClient(type, sslcontext, config, null);
    }
    
    public static PromiseHttpClient createHttpClient(IOType type, SSLContext sslcontext, Configuration config, String wsName){
        String name = config == null ? wsName : config.getString("ws.client.name",wsName);
        RequestConfig requestConfig = getDefaultRequestConfig(config);
        if(type == IOType.ASYNC_NIO){
            CloseableHttpAsyncClient asyncClient = createAsyncHttpsClient(sslcontext,config);
            if(StringUtils.isNotBlank(name)){
                return new AsyncPromiseHttpClient(asyncClient,name,requestConfig);
            }else{
                return new AsyncPromiseHttpClient(asyncClient,requestConfig);
            }
        }else{
            CloseableHttpClient syncClient = createHttpsClient(sslcontext,config);
            if(StringUtils.isNotBlank(name)){
                return new SyncPromiseHttpClient(syncClient,name,requestConfig);
            }else{
                return new SyncPromiseHttpClient(syncClient,requestConfig);
            }
        }
    }

    public static PromiseHttpClient syncHttpClientToHystrix(PromiseHttpClient httpClient,Configuration config){
        if(httpClient instanceof SyncPromiseHttpClient){
            return new HystrixHttpClient(((SyncPromiseHttpClient) httpClient).getHttpClient(),config,httpClient.getDefaultRequestConfig());
        }else{
            return httpClient;
        }
    }

    public static RequestConfig getDefaultRequestConfig(Configuration config){
        config = config != null ? config : getConfiguration();
        int connettimeout= config.getInt("ws.timeout.connet", 5 * 1000);
        int readtimeout = config.getInt("ws.timeout.socket", 10 * 1000);
        int reqtimeout = config.getInt("ws.timeout.request", 500);

        // 默认30秒时间超时
        RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(reqtimeout)
                .setConnectTimeout(connettimeout)
                .setSocketTimeout(readtimeout)
                .build();
        return defaultRequestConfig;
    }


    public static CloseableHttpAsyncClient createAsyncHttpsClient(SSLContext sslcontext, Configuration config){
        X509HostnameVerifier hostnameVerifier = new AllowAllHostnameVerifier();
        sslcontext = sslcontext == null ? createSSLContext() : sslcontext;
        
        //Create a registry of custom connection session strategies for supported
        //protocol schemes.
        Registry<SchemeIOSessionStrategy> sessionStrategyRegistry = RegistryBuilder.<SchemeIOSessionStrategy>create()
            .register("http", NoopIOSessionStrategy.INSTANCE)
            .register("https", new SSLIOSessionStrategy(sslcontext, hostnameVerifier))
            .build();

        config = config != null ? config : getConfiguration();
        int connettimeout= config.getInt("ws.timeout.connet", 5 * 1000);
        int readtimeout = config.getInt("ws.timeout.socket", 10 * 1000);
        int reqtimeout = config.getInt("ws.timeout.request", 500);
        //默认30秒时间超时
        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(Runtime.getRuntime().availableProcessors())
                .setConnectTimeout(connettimeout)
                .setSoTimeout(readtimeout)
                .build();
        
        ConnectingIOReactor ioReactor;
        try {
            ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);
        } catch (IOReactorException e) {
            throw new RuntimeException(e);
        }
        
        PoolingNHttpClientConnectionManager connManager = new PoolingNHttpClientConnectionManager(ioReactor,null,sessionStrategyRegistry,new SimpleDNSResover());
        
        // 默认30秒时间超时
        RequestConfig defaultRequestConfig = getDefaultRequestConfig(config);

        connManager.setMaxTotal(config.getInt("ws.connect.maxTotal",2000));
        connManager.setDefaultMaxPerRoute(config.getInt("ws.connect.maxPreRoute",1000));
        
        IOReactorConfig ioconfig = IOReactorConfig.custom()
                                    .setConnectTimeout(connettimeout)
                                    .setSoTimeout(readtimeout)
                                    .setTcpNoDelay(true)
                                    .setSoKeepAlive(true)
                                    .build();
        
        CloseableHttpAsyncClient httpclient = HttpAsyncClients.custom()
            .setConnectionManager(connManager)
            .setRoutePlanner(createProxyRoutePlan(config))
            .setDefaultIOReactorConfig(ioconfig)
            .setDefaultRequestConfig(defaultRequestConfig)
            .setKeepAliveStrategy(createKeepAliveStrategy(config))
            .setConnectionReuseStrategy(createConnectionReuseStrategy(config))
            .build();
        return httpclient;
    }

    public static Configuration getConfiguration() {
        Configuration conf = Configuration.empty();
        return play.api.Play.maybeApplication().isDefined() ? Play.application().configuration(): conf;
    }

    /**
     * 
     * 
     * 基于SSLContext创建HTTPS CLIENT
     * 
     * @author 尹有松
     * @param sslcontext
     * @return
     */
    public static CloseableHttpClient createHttpsClient(SSLContext sslcontext, Configuration config) {
        PoolingHttpClientConnectionManager connManager;
        X509HostnameVerifier hostnameVerifier = new AllowAllHostnameVerifier();
        sslcontext = sslcontext == null ? createSSLContext() : sslcontext;
        
        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create()
            .register("http", PlainConnectionSocketFactory.INSTANCE)
            .register("https", new SSLConnectionSocketFactory(sslcontext, new String[] { "TLSv1" }, null, hostnameVerifier))
            .build();
        connManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry,new SimpleDNSResover());//可以设置DNSResover
        
        config = config != null ? config : getConfiguration();
        
        int connettimeout= config.getInt("ws.timeout.connet", 5 * 1000);
        int readtimeout = config.getInt("ws.timeout.socket", 10 * 1000);
        int reqtimeout = config.getInt("ws.timeout.request", 500);
        RequestConfig defaultRequestConfig =  getDefaultRequestConfig(config);

        connManager.setMaxTotal(config.getInt("ws.connect.maxTotal",2000));
        connManager.setDefaultMaxPerRoute(config.getInt("ws.connect.maxPreRoute",1000));
        
        SocketConfig soconfig = SocketConfig.custom()
                            .setSoKeepAlive(true)
                            .setSoReuseAddress(false)
                            .setSoTimeout(readtimeout)
                            .build();
        
        CloseableHttpClient httpclient = HttpClients.custom()
            .setConnectionManager(connManager)
            .setRoutePlanner(createProxyRoutePlan(config))
            .setDefaultSocketConfig(soconfig)
            .setDefaultRequestConfig(defaultRequestConfig)
            .setKeepAliveStrategy(createKeepAliveStrategy(config))
            .setConnectionReuseStrategy(createConnectionReuseStrategy(config))
            .build();
        return httpclient;
    }

    public static SSLContext createSSLContext(){
        try {
            SSLContext sslcontext;
            TrustManager[] tms = new TrustManager[] { new X509TrustManager() {
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {}
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws java.security.cert.CertificateException{}
                public java.security.cert.X509Certificate[] getAcceptedIssuers(){return null;}
            } };
            sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, tms, new java.security.SecureRandom());
            return sslcontext;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static DefaultProxyRoutePlanner createProxyRoutePlan(Configuration config){
        String serverList = config.getString("ws.http.proxyServers");
        if(StringUtils.isNotBlank(serverList)){
            List<Server> servers = HostLoadBalancer.parseServerList(serverList);
            return new LoadBalancerProxyRoutePlanner(servers, DefaultSchemePortResolver.INSTANCE);
        }else{
            HttpHost proxy = createProxyServer(config);
            if(proxy == null) {
                return null;
            }else{
                List<String> proxyIgnore = config.getStringList("ws.http.proxyIgnore");
                if(proxyIgnore != null){
                    return new ExtranetRoutePlanner(proxy,proxyIgnore);
                }
                return new DefaultProxyRoutePlanner(proxy, DefaultSchemePortResolver.INSTANCE);
            }
        }
    }
    
    /**
     * 创建代理服务器
     * @author 尹有松
     * @param config
     */
    public static HttpHost createProxyServer(Configuration config) {
        Properties properties = System.getProperties();
        String host = config.getString("ws.http.proxyHost");
        if (host != null) {
            int port = config.getInt("ws.http.proxyPort", 80);
            HttpHost proxy = new HttpHost(host, port);
            return proxy;
        } else {
            host = properties.getProperty("http.proxyHost");
            if (host != null) {
                int port = Integer.valueOf(properties.getProperty("http.proxyPort", "80")).intValue();
                HttpHost proxy = new HttpHost(host, port);
                return proxy;
            }
        }
        return null;
    }
    

    public static ConnectionReuseStrategy createConnectionReuseStrategy(Configuration config) {
        final boolean reuse = config.getBoolean("ws.connection.reuse",true);
        return reuse ? DefaultConnectionReuseStrategy.INSTANCE : new NoConnectionReuseStrategy();
    }

    
    /**
     * 创建策略
     * @author 尹有松
     */
    public static ConnectionKeepAliveStrategy createKeepAliveStrategy(Configuration config){
        final long alive = config.getLong("ws.connection.alive",0L);
        if(alive <= 0){
            return null;
        }
        ConnectionKeepAliveStrategy myStrategy = new DefaultConnectionKeepAliveStrategy(){
            @Override
            public long getKeepAliveDuration(HttpResponse resp, HttpContext ctx) {
                long keepAlive = super.getKeepAliveDuration(resp, ctx);
                if (keepAlive == -1) {
                    keepAlive = alive;
                }
                return keepAlive;
            }
        };
        return myStrategy;
    }

    public static class AsyncHttpClient implements Function<Configuration, PromiseHttpClient> {
        private PromiseHttpClient httpClient;
        @Override
        public PromiseHttpClient apply(Configuration conf) {
            if(httpClient == null){
                synchronized (this) {
                    if(httpClient == null) {
                        httpClient = HttpClientFactory.createHttpClient(IOType.ASYNC_NIO, conf);
                    }
                }
            }
            return httpClient;
        }
    }

    public static class SyncHttpClient implements Function<Configuration, PromiseHttpClient> {
        private PromiseHttpClient httpClient;
        @Override
        public PromiseHttpClient apply(Configuration conf) {
            if(httpClient == null){
                synchronized (this) {
                    if(httpClient == null) {
                        httpClient = HttpClientFactory.createHttpClient(IOType.SYNC_IO, conf);
                    }
                }
            }
            return httpClient;
        }
    }
}
