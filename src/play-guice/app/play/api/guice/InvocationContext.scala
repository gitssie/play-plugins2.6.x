package play.api.guice

import com.google.common.collect.Maps
import org.aopalliance.intercept.MethodInvocation

class InvocationContext(joinPoint: MethodInvocation, interceptor: AspectInterceptor, intercepters: Array[Interceptor]) {
  val args = Maps.newHashMapWithExpectedSize[AnyRef, AnyRef](4)

  def getJoinPoint: MethodInvocation = joinPoint

  def getInterceptor: AspectInterceptor = interceptor

  def getIntercepters: Array[Interceptor] = intercepters

  @throws[Throwable]
  def recover: Any = {
    val result = this.interceptor.invoke(this)
    this.interceptor.invokerOnAfter(this, intercepters, result)
  }
}
