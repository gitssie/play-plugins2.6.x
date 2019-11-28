/**
 * Copyright 2012 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package play.libs.transport.hystrix;

import com.netflix.hystrix.HystrixCommandProperties;
import com.netflix.hystrix.util.HystrixRollingNumber;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Circuit-breaker logic that is hooked into {@link } execution and will stop allowing executions if failures have gone past the defined threshold.
 * <p>
 * It will then allow single retries after a defined sleepWindow until the execution succeeds at which point it will again close the circuit and allow executions again.
 */
public interface HystrixCircuitBreaker {

    /**
     * Every {@link } requests asks this if it is allowed to proceed or not.
     * <p>
     * This takes into account the half-open logic which allows some requests through when determining if it should be closed again.
     *
     * @return boolean whether a request should be permitted
     */
    public boolean allowRequest();

    /**
     * Whether the circuit is currently open (tripped).
     *
     * @return boolean state of circuit breaker
     */
    public boolean isOpen();

    /**
     * Invoked on successful executions from {@link } as part of feedback mechanism when in a half-open state.
     */
    public void markSuccess();

    public void markFailure();

    public static class Factory{

        public static HystrixCommandProperties.Setter createSetter(){
            HystrixCommandProperties.Setter setter = HystrixCommandProperties.Setter();
            setter.withMetricsRollingStatisticalWindowInMilliseconds(10000);// default => statisticalWindow: 10000 = 10 seconds (and default of 10 buckets so each bucket is 1 second)
            setter.withMetricsRollingStatisticalWindowBuckets(10);// default => statisticalWindowBuckets: 10 = 10 buckets in a 10 second window so each bucket is 1 second
            setter.withCircuitBreakerRequestVolumeThreshold(20);// default => statisticalWindowVolumeThreshold: 20 requests in 10 seconds must occur before statistics matter
            setter.withCircuitBreakerSleepWindowInMilliseconds(5000);// default => sleepWindow: 5000 = 5 seconds that we will sleep before trying again after tripping the circuit
            setter.withCircuitBreakerErrorThresholdPercentage(50);// default => errorThresholdPercentage = 50 = if 50%+ of requests in 10 seconds are failures or latent then we will trip the circuit
            setter.withCircuitBreakerForceOpen(false);// default => forceCircuitOpen = false (we want to allow traffic)
            setter.withCircuitBreakerForceClosed(false);// default => ignoreErrors = false
            setter.withExecutionTimeoutInMilliseconds(1000); // default => executionTimeoutInMilliseconds: 1000 = 1 second
            setter.withExecutionTimeoutEnabled(true);
            setter.withExecutionIsolationThreadInterruptOnTimeout(true);
            setter.withExecutionIsolationThreadInterruptOnFutureCancel(false);
            setter.withMetricsRollingPercentileEnabled(true);
            setter.withRequestCacheEnabled(true);
            setter.withFallbackIsolationSemaphoreMaxConcurrentRequests(10);
            setter.withFallbackEnabled(true);
            setter.withExecutionIsolationSemaphoreMaxConcurrentRequests(10);
            setter.withRequestLogEnabled(true);
            setter.withCircuitBreakerEnabled(true);
            setter.withMetricsRollingPercentileWindowInMilliseconds(60000); // default to 1 minute for RollingPercentile
            setter.withMetricsRollingPercentileWindowBuckets(6); // default to 6 buckets (10 seconds each in 60 second window)
            setter.withMetricsRollingPercentileBucketSize(100); // default to 100 values max per bucket
            setter.withMetricsHealthSnapshotIntervalInMilliseconds(500); // default to 500ms as max frequency between allowing snapshots of health (error percentage etc)
            return setter;
        }

        public static HystrixCircuitBreaker create(){
           return create(createSetter());
        }

        public static HystrixCircuitBreaker create(HystrixCommandProperties.Setter setter){
            //HystrixCommandProperties properties = new HystrixPropertiesCommandDefault(HystrixCommandKey.Factory.asKey("HystrixCircuitBreaker"),setter);
            if(setter.getCircuitBreakerEnabled()){
                return new HystrixCircuitBreakerImpl(setter);
            }else{
                return new NoOpCircuitBreaker();
            }
        }
    }

    /**
     * The default production implementation of {@link HystrixCircuitBreaker}.
     *
     * @ExcludeFromJavadoc
     * @ThreadSafe
     */
     public static class HystrixCircuitBreakerImpl implements HystrixCircuitBreaker {
        private static final Logger LOGGER = LoggerFactory.getLogger(HystrixCircuitBreaker.class);

        private final HystrixCommandProperties.Setter properties;
        private final HystrixRollingNumber counter;
        /* track whether this circuit is open/closed at any given point in time (default to false==closed) */
        private AtomicBoolean circuitOpen = new AtomicBoolean(false);

        /* when the circuit was marked open or was last allowed to try a 'singleTest' */
        private AtomicLong circuitOpenedOrLastTestedTime = new AtomicLong();

        /**
         * metricsRollingStatisticalWindowInMilliseconds:最好配置:metricsHealthSnapshotIntervalInMilliseconds
         * metricsRollingStatisticalWindowBuckets:1
         * circuitBreakerSleepWindowInMilliseconds:当所有的请求失败后,等待多久开始重试?默认为0
         * circuitBreakerErrorThresholdPercentage:失败率达到多少才启动快速失败
         * circuitBreakerRequestVolumeThreshold: 多少个请求才开始启动快速失败,建议10个
         * metricsHealthSnapshotIntervalInMilliseconds: 取样时间,建议10-15分钟为取样时间
         * @param properties
         */
        public HystrixCircuitBreakerImpl(HystrixCommandProperties.Setter properties) {
            this.properties = properties;
            this.counter = new HystrixRollingNumber(
                    properties.getMetricsRollingStatisticalWindowInMilliseconds(),
                    properties.getMetricsRollingStatisticalWindowBuckets());
        }

        public void markSuccess() {
            counter.increment(HystrixRollingNumberEvent.SUCCESS);

            if (circuitOpen.get()) {
                // TODO how can we can do this without resetting the counts so we don't lose metrics of short-circuits etc?
                resetCounter();
                // If we have been 'open' and have a success then we want to close the circuit. This handles the 'singleTest' logic
                circuitOpen.set(false);
            }
        }

        @Override
        public boolean allowRequest() {
            if (properties.getCircuitBreakerForceOpen()) {
                // properties have asked us to force the circuit open so we will allow NO requests
                return false;
            }
            if (properties.getCircuitBreakerForceClosed()) {
                // we still want to allow isOpen() to perform it's calculations so we simulate normal behavior
                isOpen();
                // properties have asked us to ignore errors so we will ignore the results of isOpen and just allow all traffic through
                return true;
            }
            return !isOpen() || allowSingleTest();
        }

        public boolean allowSingleTest() {
            long timeCircuitOpenedOrWasLastTested = circuitOpenedOrLastTestedTime.get();
            // 1) if the circuit is open
            // 2) and it's been longer than 'sleepWindow' since we opened the circuit
            if (circuitOpen.get() && System.currentTimeMillis() > timeCircuitOpenedOrWasLastTested + properties.getCircuitBreakerSleepWindowInMilliseconds()) {
                // We push the 'circuitOpenedTime' ahead by 'sleepWindow' since we have allowed one request to try.
                // If it succeeds the circuit will be closed, otherwise another singleTest will be allowed at the end of the 'sleepWindow'.
                if (circuitOpenedOrLastTestedTime.compareAndSet(timeCircuitOpenedOrWasLastTested, System.currentTimeMillis())) {
                    // if this returns true that means we set the time so we'll return true to allow the singleTest
                    // if it returned false it means another thread raced us and allowed the singleTest before we did
                    if(LOGGER.isDebugEnabled()){
                        LOGGER.debug("circuit breaker is Open,allow single,sleep time:{}",properties.getCircuitBreakerSleepWindowInMilliseconds());
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean isOpen() {
            if (circuitOpen.get()) {
                // if we're open we immediately return true and don't bother attempting to 'close' ourself as that is left to allowSingleTest and a subsequent successful test to close
                return true;
            }

            // we're closed, so let's see if errors have made us so we should trip the circuit open
            HealthCounts health = getHealthCounts();

            // check if we are past the statisticalWindowVolumeThreshold
            if (health.getTotalRequests() < properties.getCircuitBreakerRequestVolumeThreshold()) {
                // we are not past the minimum volume threshold for the statisticalWindow so we'll return false immediately and not calculate anything
                return false;
            }

            if (health.getErrorPercentage() < properties.getCircuitBreakerErrorThresholdPercentage()) {
                return false;
            } else {
                // our failure rate is too high, trip the circuit
                if (circuitOpen.compareAndSet(false, true)) {
                    // if the previousValue was false then we want to set the currentTime
                    circuitOpenedOrLastTestedTime.set(System.currentTimeMillis());
                    return true;
                } else {
                    // How could previousValue be true? If another thread was going through this code at the same time a race-condition could have
                    // caused another thread to set it to true already even though we were in the process of doing the same
                    // In this case, we know the circuit is open, so let the other thread set the currentTime and report back that the circuit is open
                    return true;
                }
            }
        }

        private volatile HealthCounts healthCountsSnapshot = new HealthCounts(0, 0, 0);
        private volatile AtomicLong lastHealthCountsSnapshot = new AtomicLong(System.currentTimeMillis());

        public HealthCounts getHealthCounts() {
            // we put an interval between snapshots so high-volume commands don't
            // spend too much unnecessary time calculating metrics in very small time periods
            long lastTime = lastHealthCountsSnapshot.get();
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastTime >= properties.getMetricsHealthSnapshotIntervalInMilliseconds() || healthCountsSnapshot == null) {
                if (lastHealthCountsSnapshot.compareAndSet(lastTime, currentTime)) {
                    // our thread won setting the snapshot time so we will proceed with generating a new snapshot
                    // losing threads will continue using the old snapshot
                    long success = counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS);
                    long failure = counter.getRollingSum(HystrixRollingNumberEvent.FAILURE); // fallbacks occur on this
                    long timeout = counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT); // fallbacks occur on this
                    long threadPoolRejected = counter.getRollingSum(HystrixRollingNumberEvent.THREAD_POOL_REJECTED); // fallbacks occur on this
                    long semaphoreRejected = counter.getRollingSum(HystrixRollingNumberEvent.SEMAPHORE_REJECTED); // fallbacks occur on this
                    long totalCount = failure + success + timeout + threadPoolRejected + semaphoreRejected;
                    long errorCount = failure + timeout + threadPoolRejected + semaphoreRejected;
                    int errorPercentage = 0;

                    if (totalCount > 0) {
                        errorPercentage = (int) ((double) errorCount / totalCount * 100);
                    }

                    healthCountsSnapshot = new HealthCounts(totalCount, errorCount, errorPercentage);
                }
            }
            return healthCountsSnapshot;
        }

        public void resetCounter() {
            // TODO can we do without this somehow?
            counter.reset();
            lastHealthCountsSnapshot.set(System.currentTimeMillis());
            healthCountsSnapshot = new HealthCounts(0, 0, 0);
        }

        public void markFailure() {
            counter.increment(HystrixRollingNumberEvent.FAILURE);
        }

    }

    public static class HealthCounts {
        private final long totalCount;
        private final long errorCount;
        private final int errorPercentage;

        public HealthCounts(long total, long error, int errorPercentage) {
            this.totalCount = total;
            this.errorCount = error;
            this.errorPercentage = errorPercentage;
        }

        public long getTotalRequests() {
            return totalCount;
        }

        public long getErrorCount() {
            return errorCount;
        }

        public int getErrorPercentage() {
            return errorPercentage;
        }
    }

    /**
     * An implementation of the circuit breaker that does nothing.
     *
     * @ExcludeFromJavadoc
     */
    public static class NoOpCircuitBreaker implements HystrixCircuitBreaker {

        @Override
        public boolean allowRequest() {
            return true;
        }

        @Override
        public boolean isOpen() {
            return false;
        }

        @Override
        public void markSuccess() {

        }

        @Override
        public void markFailure() {

        }

    }

}