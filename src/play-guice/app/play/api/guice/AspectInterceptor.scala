package play.api.guice

import org.aopalliance.intercept.{MethodInterceptor, MethodInvocation}
import play.api.Play
import play.api.inject.Injector

class AspectInterceptor extends MethodInterceptor{

  override def invoke(msign: MethodInvocation): AnyRef = {
    val method = msign.getMethod
    val an = method.getAnnotation(classOf[With])
    val clazz = an.value
    val instance = clazz.map(cls => injector.instanceOf(cls))
    val context = new InvocationContext(msign, this, instance)
    val beforeResult = invokerOnBefor(context, instance)
    if (beforeResult != null) return beforeResult
    val result = invoke(context) //如果这里出现异常
    val cb = invokerOnAfter(context, instance, result)
    if(cb == null){
      result
    }else{
      cb
    }
  }

  @throws[Throwable]
  def invokerOnBefor(context: InvocationContext, instance: Array[Interceptor]): AnyRef = {
    var r:AnyRef = null
    instance.foreach(inst => {
      if(r == null){
        r = inst.onBefor(context)
      }
    })
    r
  }

  @throws[Throwable]
  def invoke(context: InvocationContext): AnyRef = context.getJoinPoint.proceed()

  @throws[Throwable]
  def invokerOnAfter(context: InvocationContext, instance: Array[Interceptor], result: AnyRef): AnyRef = {
    var r:AnyRef = null
    instance.foreach(inst => {
      if(r == null){
        r = inst.onAfter(context,result)
      }
    })
    r
  }

  def injector: Injector = {
    Play.privateMaybeApplication match {
      case None => sys.error("There is no started application")
      case Some(app) => app.injector
    }
  }
}
