package play.libs.transport.loadbalancer;

public interface RetryHandler {

    public static final RetryHandler DEFAULT = new DefaultLoadBalancerRetryHandler();
    
    /**
     * Test if an exception is retriable for the load balancer
     * 
     * @param e the original exception
     * @param sameServer if true, the method is trying to determine if retry can be 
     *        done on the same server. Otherwise, it is testing whether retry can be
     *        done on a different server
     */
    public boolean isRetriableException(Throwable e, boolean sameServer);

    /**
     * Test if an exception should be treated as circuit failure. For example, 
     * a {@link ConnectException} is a circuit failure. This is used to determine
     * whether successive exceptions of such should trip the circuit breaker to a particular
     * host by the load balancer. If false but a server response is absent, 
     * load balancer will also close the circuit upon getting such exception.
     */
    public boolean isCircuitTrippingException(Throwable e);
        
    /**
     * @return Number of maximal retries to be done on one server
     */
    public int getMaxRetriesOnSameServer();

    /**
     * @return Number of maximal different servers to retry
     */
    public int getMaxRetriesOnNextServer();
}
