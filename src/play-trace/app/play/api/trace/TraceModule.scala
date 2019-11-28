package play.api.trace

import javax.inject.{Inject, Provider, Singleton}

import brave.Tracer
import com.google.inject.AbstractModule
import com.google.inject.matcher.Matchers
import org.slf4j.MDC


class TraceModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[ZipkinTraceServiceLike]).to(classOf[ZipkinTraceService])
    bind(classOf[Tracer]).toProvider(classOf[TracerProvider])
    bindInterceptor(Matchers.any, Matchers.annotatedWith(classOf[Traced]), new TraceInterceptor)

    bindTraceMDC()
  }

  def bindTraceMDC(): Unit ={
    val delegate = MDC.getMDCAdapter
    Option(classOf[MDC].getDeclaredField("mdcAdapter")).foreach{f =>
      f.setAccessible(true)
      f.set(null,new HttpMDCAdapter(delegate))
    }
  }
}

@Singleton
class TracerProvider @Inject() (tracerService : ZipkinTraceServiceLike) extends Provider[Tracer] {
  override def get() = TraceService.getTracer
}
