package play.api.trace

import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

import org.aopalliance.intercept.{MethodInterceptor, MethodInvocation}
import org.apache.commons.lang3.StringUtils
import play.mvc.Http


class TraceInterceptor extends MethodInterceptor{

  override def invoke(methodInvocation: MethodInvocation): AnyRef = {
    val tracer = TraceService.getTracer
    val ctx = Option(Http.Context.current.get())
    var currentSpan = Option(tracer.currentSpan())
    var requestScope:Option[AtomicReference[Closeable]] = None
    var inScope = false
    if(currentSpan.isEmpty && ctx.isDefined){
      val attr = ctx.get.request().attrs()
      currentSpan = attr.underlying().get(TraceService.requestSpan)
      requestScope = attr.underlying().get(TraceService.requestScope)
    }

    val span = currentSpan.map(p => tracer.newChild(p.context())).getOrElse(tracer.newTrace())
    val traced:Traced = methodInvocation.getMethod.getAnnotation(classOf[Traced])
    span.name(StringUtils.defaultIfBlank(traced.name(),methodInvocation.getMethod.getDeclaringClass.getSimpleName + "." + methodInvocation.getMethod.getName))
    span.start()
    val scope = tracer.withSpanInScope(span)
    if(requestScope.isDefined) {
      requestScope.map(s => inScope = s.compareAndSet(null,scope))
    }
    try{
      methodInvocation.proceed()
    }catch{
      case e : Throwable => span.tag("error",StringUtils.defaultString(e.getMessage,e.getClass.getSimpleName));throw e
    }finally {
      span.finish()
      if(!inScope){
        scope.close()
      }
    }
  }

}
