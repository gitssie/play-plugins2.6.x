package play.api.trace

import brave.internal.HexCodec
import brave.propagation.TraceContext
import org.slf4j.MDC
import play.api.UsefulException

class TraceableException(span:TraceContext,c: Throwable) extends UsefulException(c.getMessage,c){
    this.title = c.getMessage
    this.description = c.getMessage
    this.cause = c
    this.id = span.traceIdString()

  override def getStackTrace: Array[StackTraceElement] = {
    MDC.put("traceId", span.traceIdString)
    MDC.put("spanId", HexCodec.toLowerHex(span.spanId))
    super.getStackTrace
  }

  override def fillInStackTrace (): Throwable = this
}
