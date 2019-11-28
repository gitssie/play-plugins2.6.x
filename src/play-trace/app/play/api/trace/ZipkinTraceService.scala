package play.api.trace

import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import brave.context.slf4j.MDCCurrentTraceContext
import brave.sampler.Sampler
import brave.{Span, Tracer, Tracing}
import play.api.Configuration
import play.api.inject.ApplicationLifecycle
import play.api.libs.typedmap.TypedKey
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.okhttp3.OkHttpSender

import scala.concurrent.Future

object TraceService {
  private [trace] val tracing = new AtomicReference[Tracing]()

  lazy val tracer :Tracer = tracing.get().tracer()

  def getTracer = tracer

  val requestSpan = TypedKey[Span]("request span")
  val requestScope = TypedKey[AtomicReference[Closeable]]("request scope")
}

/**
 * Class for Zipkin tracing at Play2.6.
 *
 * @param conf a Play's configuration
 * @param actorSystem a Play's actor system
 */
@Singleton
class ZipkinTraceService @Inject() (conf: Configuration, actorSystem: ActorSystem, applicationLifecycle: ApplicationLifecycle) extends ZipkinTraceServiceLike {

  //implicit val executionContext: ExecutionContext = actorSystem.dispatchers.lookup(ZipkinTraceConfig.AkkaName)

  val tracing:Tracing = {
    val build = Tracing.newBuilder()
      .localServiceName(conf.getOptional[String](ZipkinTraceConfig.ServiceName).getOrElse(conf.getOptional[String]("play.ddsl.service.name").getOrElse("play")))
      .currentTraceContext(MDCCurrentTraceContext.create(new HttpTraceContext()))
      //.localEndpoint(null)
    conf.getOptional[String](ZipkinTraceConfig.ZipkinBaseUrl).foreach(ul => {
      var url = ul
      if(!url.endsWith("spans")){
         url = url + "/api/v2/spans"
      }
      val sender = OkHttpSender.create(url)
      build.spanReporter(AsyncReporter.create(sender))
      applicationLifecycle.addStopHook(() => Future.successful(sender.close))
    })

    build.sampler(conf.getOptional[String](ZipkinTraceConfig.ZipkinSampleRate)
        .map(s => Sampler.create(s.toFloat)) getOrElse Sampler.ALWAYS_SAMPLE
      )

    build.build()
  }

  TraceService.tracing.set(tracing)
}