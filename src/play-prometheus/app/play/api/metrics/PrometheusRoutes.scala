package play.api.metrics


import play.api.mvc._
import play.core.routing.HandlerInvokerFactory._
import play.core.routing._

class PrometheusRoutes(
              override val errorHandler: play.api.http.HttpErrorHandler,
              // @LINE:2
              PrometheusController_0: com.github.stijndehaes.playprometheusfilters.controllers.PrometheusController,
              val prefix: String
            ) extends GeneratedRouter {

  @javax.inject.Inject()
  def this(errorHandler: play.api.http.HttpErrorHandler,
           // @LINE:2
           PrometheusController_0: com.github.stijndehaes.playprometheusfilters.controllers.PrometheusController
          ) = this(errorHandler, PrometheusController_0, "/")

  def withPrefix(prefix: String): PrometheusRoutes = {
    new PrometheusRoutes(errorHandler, PrometheusController_0, prefix)
  }

  private[this] val defaultPrefix: String = {
    if (this.prefix.endsWith("/")) "" else "/"
  }

  def documentation = List(
    ("""GET""", this.prefix + (if(this.prefix.endsWith("/")) "" else "/") + """metrics""", """com.github.stijndehaes.playprometheusfilters.controllers.PrometheusController.getMetrics"""),
    Nil
  ).foldLeft(List.empty[(String,String,String)]) { (s,e) => e.asInstanceOf[Any] match {
    case r @ (_,_,_) => s :+ r.asInstanceOf[(String,String,String)]
    case l => s ++ l.asInstanceOf[List[(String,String,String)]]
  }}


  // @LINE:2
  private[this] lazy val com_github_stijndehaes_playprometheusfilters_controllers_PrometheusController_getMetrics0_route = Route("GET",
    PathPattern(List(StaticPart(this.prefix), StaticPart(this.defaultPrefix), StaticPart("metrics")))
  )
  private[this] lazy val com_github_stijndehaes_playprometheusfilters_controllers_PrometheusController_getMetrics0_invoker = createInvoker(
    PrometheusController_0.getMetrics,
    play.api.routing.HandlerDef(this.getClass.getClassLoader,
      "com.github.gitssie",
      "com.github.stijndehaes.playprometheusfilters.controllers.PrometheusController",
      "getMetrics",
      Nil,
      "GET",
      this.prefix + """metrics""",
      """""",
      Seq()
    )
  )


  def routes: PartialFunction[RequestHeader, Handler] = {

    // @LINE:2
    case com_github_stijndehaes_playprometheusfilters_controllers_PrometheusController_getMetrics0_route(params@_) =>
      call {
        com_github_stijndehaes_playprometheusfilters_controllers_PrometheusController_getMetrics0_invoker.call(PrometheusController_0.getMetrics)
      }
  }
}