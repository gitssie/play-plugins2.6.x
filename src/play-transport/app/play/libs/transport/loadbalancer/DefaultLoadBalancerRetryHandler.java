package play.libs.transport.loadbalancer;

import com.google.common.collect.Lists;
import play.Configuration;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.List;
import org.apache.http.ConnectionClosedException;
/**
 * A default {@link RetryHandler}. The implementation is limited to
 * known exceptions in java.net. Specific client implementation should provide its own
 * {@link RetryHandler}
 * 
 * @author awang
 */
public class DefaultLoadBalancerRetryHandler implements RetryHandler {

    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> retriable = Lists.newArrayList(ConnectException.class,ConnectionClosedException.class);
    
    @SuppressWarnings("unchecked")
    private List<Class<? extends Throwable>> circuitRelated = Lists.newArrayList(SocketException.class);

    protected final int retrySameServer;
    protected final int retryNextServer;
    protected final boolean retryEnabled;

    public DefaultLoadBalancerRetryHandler() {
        this.retrySameServer = 0;
        this.retryNextServer = 0;
        this.retryEnabled = false;
    }
    
    public DefaultLoadBalancerRetryHandler(int retrySameServer, int retryNextServer, boolean retryEnabled) {
        this.retrySameServer = retrySameServer;
        this.retryNextServer = retryNextServer;
        this.retryEnabled = retryEnabled;
    }
    
    public DefaultLoadBalancerRetryHandler(Configuration conf) {
        this.retrySameServer = conf.getInt("ws.http.maxAutoRetries",0);
        this.retryNextServer = conf.getInt("ws.http.maxAutoRetriesNextServer",0);
        this.retryEnabled = conf.getBoolean("ws.http.enabledAutoRetries",false);
    }
    
    @Override
    public boolean isRetriableException(Throwable e, boolean sameServer) {
        if (retryEnabled) {
            return isPresentAsCause(e, getRetriableExceptions());
        }
        return false;
    }

    public static boolean isPresentAsCause(Throwable throwableToSearchIn,
        Collection<Class<? extends Throwable>> throwableToSearchFor) {
    int infiniteLoopPreventionCounter = 5;
    while (throwableToSearchIn != null && infiniteLoopPreventionCounter > 0) {
        infiniteLoopPreventionCounter--;
        for (Class<? extends Throwable> c: throwableToSearchFor) {
            if (c.isAssignableFrom(throwableToSearchIn.getClass())) {
                return true;
            }
        }
        throwableToSearchIn = throwableToSearchIn.getCause();
    }
    return false;
    }
    /**
     * @return true if {@link SocketException} or {@link SocketTimeoutException} is a cause in the Throwable.
     */
    @Override
    public boolean isCircuitTrippingException(Throwable e) {
        return isPresentAsCause(e, getCircuitRelatedExceptions());        
    }

    @Override
    public int getMaxRetriesOnSameServer() {
        return retrySameServer;
    }

    @Override
    public int getMaxRetriesOnNextServer() {
        return retryNextServer;
    }
    
    protected List<Class<? extends Throwable>> getRetriableExceptions() {
        return retriable;
    }
    
    protected List<Class<? extends Throwable>>  getCircuitRelatedExceptions() {
        return circuitRelated;
    }
}