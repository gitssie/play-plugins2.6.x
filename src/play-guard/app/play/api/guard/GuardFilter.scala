package play.api.guard

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

import akka.stream.Materializer
import com.google.common.cache.{CacheBuilder, CacheLoader}
import com.netflix.hystrix.exception.HystrixRuntimeException
import play.api.mvc.{Filter, RequestHeader, Result}
import play.api.routing.Router.Attrs

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class GuardFilter @Inject()(implicit val ex:ExecutionContext,val mat: Materializer) extends Filter{

  val circuitBreaker = CacheBuilder.newBuilder()
                                    .expireAfterAccess(10,TimeUnit.MINUTES)
                                    .build(new CacheLoader[String,HystrixCircuitBreaker] {
                                        override def load(k: String) = CircuitBreakerFactory.create()
                                    })

  override def apply(nextFilter: RequestHeader => Future[Result])(req: RequestHeader): Future[Result] = {
    req.attrs.get(Attrs.HandlerDef).map(handler =>{
      val tag = handler.controller + "." + handler.method
      val circuit = circuitBreaker.get(tag)
      if(circuit.allowRequest()){
        val result = nextFilter(req)
        result.onComplete{
          case Failure(_) => circuit.markFailure()
          case Success(_) => circuit.markSuccess()
        }
        result
      }else{
        val shortCircuitException = new RuntimeException("GuardFilter circuit short-circuited and is OPEN")
        val exception = new HystrixRuntimeException(HystrixRuntimeException.FailureType.SHORTCIRCUIT, null, "GuardFilter", shortCircuitException, null)
        Future.failed(exception)
      }
    }).getOrElse(nextFilter(req))
  }
}
