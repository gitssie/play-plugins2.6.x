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
  *//**
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
package play.api.guard

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import com.netflix.hystrix.{HystrixCommandKey, HystrixCommandProperties}
import com.netflix.hystrix.strategy.properties.HystrixPropertiesCommandDefault
import com.netflix.hystrix.util.{HystrixRollingNumber, HystrixRollingNumberEvent}


trait HystrixCircuitBreaker {
  /**
    * Every {@link } requests asks this if it is allowed to proceed or not.
    * <p>
    * This takes into account the half-open logic which allows some requests through when determining if it should be closed again.
    *
    * @return boolean whether a request should be permitted
    */
  def allowRequest(): Boolean

  /**
    * Whether the circuit is currently open (tripped).
    *
    * @return boolean state of circuit breaker
    */
  def isOpen(): Boolean

  /**
    * Invoked on successful executions from {@link } as part of feedback mechanism when in a half-open state.
    */
  def markSuccess(): Unit

  def markFailure(): Unit
}

/**
  * Circuit-breaker logic that is hooked into {@link } execution and will stop allowing executions if failures have gone past the defined threshold.
  * <p>
  * It will then allow single retries after a defined sleepWindow until the execution succeeds at which point it will again close the circuit and allow executions again.
  */
object CircuitBreakerFactory {

  def createSetter: HystrixCommandProperties.Setter = {
    val setter = HystrixCommandProperties.Setter()
    setter.withMetricsRollingStatisticalWindowInMilliseconds(10000) // default => statisticalWindow: 10000 = 10 seconds (and default of 10 buckets so each bucket is 1 second)
    setter.withMetricsRollingStatisticalWindowBuckets(10) // default => statisticalWindowBuckets: 10 = 10 buckets in a 10 second window so each bucket is 1 second
    setter.withCircuitBreakerRequestVolumeThreshold(20) // default => statisticalWindowVolumeThreshold: 20 requests in 10 seconds must occur before statistics matter
    setter.withCircuitBreakerSleepWindowInMilliseconds(5000) // default => sleepWindow: 5000 = 5 seconds that we will sleep before trying again after tripping the circuit
    setter.withCircuitBreakerErrorThresholdPercentage(50) // default => errorThresholdPercentage = 50 = if 50%+ of requests in 10 seconds are failures or latent then we will trip the circuit
    setter.withCircuitBreakerForceOpen(false) // default => forceCircuitOpen = false (we want to allow traffic)
    setter.withCircuitBreakerForceClosed(false) // default => ignoreErrors = false
    setter.withExecutionTimeoutInMilliseconds(1000) // default => executionTimeoutInMilliseconds: 1000 = 1 second
    setter.withExecutionTimeoutEnabled(true)
    setter.withExecutionIsolationThreadInterruptOnTimeout(true)
    setter.withExecutionIsolationThreadInterruptOnFutureCancel(false)
    setter.withMetricsRollingPercentileEnabled(true)
    setter.withRequestCacheEnabled(true)
    setter.withFallbackIsolationSemaphoreMaxConcurrentRequests(10)
    setter.withFallbackEnabled(true)
    setter.withExecutionIsolationSemaphoreMaxConcurrentRequests(10)
    setter.withRequestLogEnabled(true)
    setter.withCircuitBreakerEnabled(true)
    setter.withMetricsRollingPercentileWindowInMilliseconds(60000) // default to 1 minute for RollingPercentile
    setter.withMetricsRollingPercentileWindowBuckets(6) // default to 6 buckets (10 seconds each in 60 second window)
    setter.withMetricsRollingPercentileBucketSize(100) // default to 100 values max per bucket
    setter.withMetricsHealthSnapshotIntervalInMilliseconds(500) // default to 500ms as max frequency between allowing snapshots of health (error percentage etc)
    setter
  }

  def create(): HystrixCircuitBreaker = create(createSetter)

  def create(setter: HystrixCommandProperties.Setter): HystrixCircuitBreaker = {
    if (setter.getCircuitBreakerEnabled) {
      new HystrixCircuitBreakerImpl(setter)
    } else {
      new NoOpCircuitBreaker
    }
  }
}

/**
  * metricsRollingStatisticalWindowInMilliseconds
  * metricsRollingStatisticalWindowBuckets
  * circuitBreakerSleepWindowInMilliseconds:当所有的请求失败后,等待多久开始重试
  * circuitBreakerErrorThresholdPercentage:失败率达到多少才启动快速失败
  * circuitBreakerRequestVolumeThreshold: 多少个请求才开始启动快速失败
  * metricsHealthSnapshotIntervalInMilliseconds: 取样时间
  *
  * @param properties
  */
class HystrixCircuitBreakerImpl(val properties: HystrixCommandProperties.Setter) extends HystrixCircuitBreaker {

  private val counter = new HystrixRollingNumber(properties.getMetricsRollingStatisticalWindowInMilliseconds, properties.getMetricsRollingStatisticalWindowBuckets)
  /* track whether this circuit is open/closed at any given point in time (default to false==closed) */
  private val circuitOpen = new AtomicBoolean(false)
  /* when the circuit was marked open or was last allowed to try a 'singleTest' */
  private val circuitOpenedOrLastTestedTime = new AtomicLong

  override def markSuccess(): Unit = {
    counter.increment(HystrixRollingNumberEvent.SUCCESS)
    if (circuitOpen.get) {
      resetCounter()
      // If we have been 'open' and have a success then we want to close the circuit. This handles the 'singleTest' logic
      circuitOpen.set(false)
    }
  }

  override def allowRequest(): Boolean = {
    if (properties.getCircuitBreakerForceOpen) { // properties have asked us to force the circuit open so we will allow NO requests
      return false
    }
    if (properties.getCircuitBreakerForceClosed) { // we still want to allow isOpen() to perform it's calculations so we simulate normal behavior
      // properties have asked us to ignore errors so we will ignore the results of isOpen and just allow all traffic through
      return true
    }
    !isOpen || allowSingleTest
  }

  def allowSingleTest: Boolean = {
    val timeCircuitOpenedOrWasLastTested = circuitOpenedOrLastTestedTime.get
    // 1) if the circuit is open
    // 2) and it's been longer than 'sleepWindow' since we opened the circuit
    if (circuitOpen.get && System.currentTimeMillis > timeCircuitOpenedOrWasLastTested + properties.getCircuitBreakerSleepWindowInMilliseconds) { // We push the 'circuitOpenedTime' ahead by 'sleepWindow' since we have allowed one request to try.
      // If it succeeds the circuit will be closed, otherwise another singleTest will be allowed at the end of the 'sleepWindow'.
      if (circuitOpenedOrLastTestedTime.compareAndSet(timeCircuitOpenedOrWasLastTested, System.currentTimeMillis)) { // if this returns true that means we set the time so we'll return true to allow the singleTest
        // if it returned false it means another thread raced us and allowed the singleTest before we did
        return true
      }
    }
    false
  }

  override def isOpen(): Boolean = {
    if (circuitOpen.get) { // if we're open we immediately return true and don't bother attempting to 'close' ourself as that is left to allowSingleTest and a subsequent successful test to close
      return true
    }
    // we're closed, so let's see if errors have made us so we should trip the circuit open
    val health = getHealthCounts
    // check if we are past the statisticalWindowVolumeThreshold
    if (health.getTotalRequests < properties.getCircuitBreakerRequestVolumeThreshold) { // we are not past the minimum volume threshold for the statisticalWindow so we'll return false immediately and not calculate anything
      return false
    }
    if (health.getErrorPercentage < properties.getCircuitBreakerErrorThresholdPercentage) {
      false
    } else { // our failure rate is too high, trip the circuit
      if (circuitOpen.compareAndSet(false, true)) { // if the previousValue was false then we want to set the currentTime
        circuitOpenedOrLastTestedTime.set(System.currentTimeMillis)
        true
      }else { // How could previousValue be true? If another thread was going through this code at the same time a race-condition could have
        // caused another thread to set it to true already even though we were in the process of doing the same
        // In this case, we know the circuit is open, so let the other thread set the currentTime and report back that the circuit is open
        true
      }
    }
  }

  private var healthCountsSnapshot = new HealthCounts(0, 0, 0)
  private val lastHealthCountsSnapshot = new AtomicLong(System.currentTimeMillis)

  def getHealthCounts: HealthCounts = { // we put an interval between snapshots so high-volume commands don't
    // spend too much unnecessary time calculating metrics in very small time periods
    val lastTime = lastHealthCountsSnapshot.get
    val currentTime = System.currentTimeMillis
    if (currentTime - lastTime >= properties.getMetricsHealthSnapshotIntervalInMilliseconds || healthCountsSnapshot == null) if (lastHealthCountsSnapshot.compareAndSet(lastTime, currentTime)) { // our thread won setting the snapshot time so we will proceed with generating a new snapshot
      // losing threads will continue using the old snapshot
      val success = counter.getRollingSum(HystrixRollingNumberEvent.SUCCESS)
      val failure = counter.getRollingSum(HystrixRollingNumberEvent.FAILURE)
      // fallbacks occur on this
      val timeout = counter.getRollingSum(HystrixRollingNumberEvent.TIMEOUT)
      val threadPoolRejected = counter.getRollingSum(HystrixRollingNumberEvent.THREAD_POOL_REJECTED)
      val semaphoreRejected = counter.getRollingSum(HystrixRollingNumberEvent.SEMAPHORE_REJECTED)
      val totalCount = failure + success + timeout + threadPoolRejected + semaphoreRejected
      val errorCount = failure + timeout + threadPoolRejected + semaphoreRejected
      var errorPercentage = 0
      if (totalCount > 0) errorPercentage = (errorCount.toDouble / totalCount * 100).toInt
      healthCountsSnapshot = new HealthCounts(totalCount, errorCount, errorPercentage)
    }
    healthCountsSnapshot
  }

  def resetCounter(): Unit = {
    counter.reset()
    lastHealthCountsSnapshot.set(System.currentTimeMillis)
    healthCountsSnapshot = new HealthCounts(0, 0, 0)
  }

  override def markFailure(): Unit = counter.increment(HystrixRollingNumberEvent.FAILURE)
}

private[guard] class HealthCounts(val totalCount: Long, val errorCount: Long, val errorPercentage: Int) {
  def getTotalRequests: Long = totalCount

  def getErrorCount: Long = errorCount

  def getErrorPercentage: Int = errorPercentage
}

class NoOpCircuitBreaker extends HystrixCircuitBreaker {
  override def allowRequest = true

  override def isOpen = false

  override def markSuccess(): Unit = {}

  override def markFailure(): Unit = {}
}