package play.api.trace

import brave.propagation.CurrentTraceContext.Default
import brave.propagation.{CurrentTraceContext, TraceContext}
import play.mvc.Http

class HttpTraceContext(delegate: CurrentTraceContext) extends CurrentTraceContext{
  val key:String = classOf[HttpTraceContext].getName
  val context = Http.Context.current

  def this() = this(Default.create())

  def getInContext: TraceContext = if(context.get() != null){
    context.get().args.get(key).asInstanceOf[TraceContext]
  }else{
    null
  }

  def putInContext(currentSpan: TraceContext):Unit = if(context.get() != null){
    context.get().args.put(key,currentSpan)
  }

  override def newScope(currentSpan: TraceContext) = {
    if(context.get() != null) {
      val previous = getInContext
      putInContext(currentSpan)

      new CurrentTraceContext.Scope {
        override def close(): Unit = {
          putInContext(previous)
        }
      }
    }else{
      delegate.newScope(currentSpan)
    }
  }

  override def get():TraceContext = {
    var ctx = getInContext
    if(ctx == null){
      ctx = delegate.get()
    }
    ctx
  }
}
