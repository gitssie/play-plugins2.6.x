package play.api.guice

trait Interceptor {
  @throws[Throwable]
  def onBefor(context: InvocationContext): AnyRef

  @throws[Throwable]
  def onAfter(context: InvocationContext, result: AnyRef): AnyRef
}