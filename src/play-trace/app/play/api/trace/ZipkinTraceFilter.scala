package play.api.trace

import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference
import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import play.api.mvc.{Filter, RequestHeader, Result}
import play.api.routing.Router

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

/**
 * A Zipkin filter.
 *
 * This filter is that reports how long a request takes to execute in Play as a server span.
 * The way to use this filter is following:
 * {{{
 * class Filters @Inject() (
 *   zipkin: ZipkinTraceFilter
 * ) extends DefaultHttpFilters(zipkin)
 * }}}
 *
 * @param tracer a Zipkin tracer
 * @param mat a materializer
 */
@Singleton
class ZipkinTraceFilter @Inject()(tracer: ZipkinTraceServiceLike,implicit val ex:ExecutionContext)(implicit val mat: Materializer) extends Filter {

  private val reqHeaderToSpanName: RequestHeader => String = ZipkinTraceFilter.ParamAwareRequestNamer

  private val assetsControllerName = classOf[controllers.Assets].getName

  def apply(nextFilter: (RequestHeader) => Future[Result])(req: RequestHeader): Future[Result] = {
    val notFilter = notAllowTrace(req)
    if(notFilter){
      nextFilter(req)
    }else {
      val serverSpan = tracer.serverReceived(
        spanName = reqHeaderToSpanName(req),
        span = tracer.newSpan(req.headers)((headers, key) => headers.get(key))
      )
      serverSpan.tag("client.address", req.remoteAddress)
      val spanContext = serverSpan.context();
      val scopeRef = new AtomicReference[Closeable]()
      val nextReq = req.addAttr(TraceService.requestSpan, serverSpan)
        .addAttr(TraceService.requestScope, scopeRef)

      val result = nextFilter(nextReq)
      result.onComplete {
        case Failure(t) => {
          tracer.serverSend(serverSpan, "failed" -> s"Finished with exception: ${t.getMessage}")
          Option(scopeRef.get()).foreach(_.close())
        }
        case Success(r) => {
          serverSpan.tag("status.code", r.header.status.toString)
          tracer.serverSend(serverSpan)
          Option(scopeRef.get()).foreach(_.close())
        }
      }
      result.transform(s => s,t => new TraceableException(spanContext,t))
    }
  }

  def notAllowTrace(req:RequestHeader):Boolean = {
    req.attrs.get(Router.Attrs.HandlerDef).map(h => {
      h.controller == assetsControllerName
    }).getOrElse(true)
  }
}

object ZipkinTraceFilter {
  val ParamAwareRequestNamer: RequestHeader => String = { reqHeader =>
    s"${reqHeader.method} - ${reqHeader.path}"
  }
}