package play.api.routes

import javax.inject.Singleton

import play.api.mvc._
import play.core.routing.HandlerInvokerFactory._
import play.core.routing._

@Singleton
class AssetsRoutes(
  override val errorHandler: play.api.http.HttpErrorHandler,
  Assets_0: controllers.Assets,
  val prefix: String
) extends GeneratedRouter{

   @javax.inject.Inject()
   def this(errorHandler: play.api.http.HttpErrorHandler,
    Assets_0: controllers.Assets,
  ) = this(errorHandler, Assets_0,"/")

  def withPrefix(prefix: String): AssetsRoutes = {
    new AssetsRoutes(errorHandler, Assets_0,prefix)
  }

  private[this] val defaultPrefix: String = {
    if (this.prefix.endsWith("/")) "" else "/"
  }

  def documentation = List(
    ("""GET""", this.prefix + (if(this.prefix.endsWith("/")) "" else "/") + """assets/""" + "$" + """(.*/)?views/<.+>""", """Unauthorized resources from the /public contain /views folder"""),
    ("""GET""", this.prefix + (if(this.prefix.endsWith("/")) "" else "/") + """assets/""" + "$" + """file<.+>""", """controllers.Assets.at(path:String = "/public", file:String)"""),
    Nil
  ).foldLeft(List.empty[(String,String,String)]) { (s,e) => e.asInstanceOf[Any] match {
    case r @ (_,_,_) => s :+ r.asInstanceOf[(String,String,String)]
    case l => s ++ l.asInstanceOf[List[(String,String,String)]]
  }}


  // @LINE:10
  private[this] lazy val controllers_Assets_at0_route = Route("GET",
    PathPattern(List(StaticPart(this.prefix), StaticPart(this.defaultPrefix), StaticPart("assets/"), DynamicPart("file", """.+""",false)))
  )

  private[this] lazy val controllers_Assets_at0_safe_route = Route("GET",
    PathPattern(List(StaticPart(this.prefix), StaticPart(this.defaultPrefix), StaticPart("assets/"), DynamicPart("file", """(.*/)?views/.+""",false)))
  )

  private[this] lazy val controllers_Assets_at0_invoker = createInvoker(
    Assets_0.at(fakeValue[String], fakeValue[String]),
    play.api.routing.HandlerDef(this.getClass.getClassLoader,
      "play.api.routes",
      "controllers.Assets",
      "at",
      Seq(classOf[String], classOf[String]),
      "GET",
      this.prefix + """assets/""" + "$" + """file<.+>""",
      """Map static resources from the /public folder to the /assets URL path""",
      Seq("deadbolt:subjectNotPresent")
    )
  )

  private[this] lazy val controllers_Assets_at0_safe_invoker = createInvoker(
    badRequest(fakeValue[String]),
    play.api.routing.HandlerDef(this.getClass.getClassLoader,
      "play.api.routes",
      "controllers.Assets",
      "at",
      Seq(classOf[String], classOf[String]),
      "GET",
      this.prefix + """assets/""" + "$" + """file<.+>""",
      "Unauthorized static resources from the /public folder to the /assets URL path",
      Seq()
    )
  )

  def routes: PartialFunction[RequestHeader, Handler] = {

    case controllers_Assets_at0_safe_route(params@_) =>
      call(Param[String]("path", Right("/public")), params.fromPath[String]("file", None)) { (path, file) =>
        controllers_Assets_at0_safe_invoker.call(badRequest("Unauthorized static resources"))
      }
    case controllers_Assets_at0_route(params@_) =>
      call(Param[String]("path", Right("/public")), params.fromPath[String]("file", None)) { (path, file) =>
        controllers_Assets_at0_invoker.call(Assets_0.at(path, file))
      }
  }
}
