// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied. See the License for the specific language governing
// permissions and limitations under the License.

package play.api.routes

import javax.inject.{Inject, Singleton}

import play.api.inject.Injector
import play.api.mvc.{Handler, RequestHeader}
import play.api.routing.{Router => PlayRouter}
import play.api.{Configuration, Environment}
import play.utils.Reflect

/**
  * Play Router Inject by Configuration
  * syntax: IntOrder:Path->RouterClass
  * example[0]: 100:/echo-> play.api.echo.Routes
  * example[1]:     /echo-> play.api.echo.Routes
  * example[2]:             play.api.echo.Routes
  * the default order number is 1000
  * @param env
  * @param conf
  * @param inject
  */
@Singleton
class Router @Inject()(env:Environment,conf:Configuration,inject:Injector,errorHandler: play.api.http.HttpErrorHandler) extends PlayRouter {
  private val orderSplitAt = ":"
  private val authSplitAt = "+"
  private val pathSplitAt = "->"
  private val dfltSort = 1000

  val includes:Seq[PlayRouter] = {
    val includes = conf.getOptional[Seq[String]]("play.routes.enabled").getOrElse(Seq.empty)
    val excludes = conf.getOptional[Seq[String]]("play.routes.disabled").getOrElse(Seq.empty)

    val routesClassNames = includes.filter(i => excludes.find(i.indexOf(_) >= 0).isEmpty)
    routesClassNames.map(line => {
        var idx = 0
        var end = line.trim
        idx = end.lastIndexOf(authSplitAt)
        if(idx > 0){
          end = end.substring(0,idx)
        }
        idx = line.trim.indexOf(pathSplitAt)
        if(idx >= 0){
          val part = end.substring(0,idx).trim
          end = end.substring(idx+pathSplitAt.length,end.length).trim
          idx = part.indexOf(orderSplitAt)
          if(idx > 0){
            ((part.substring(0,idx).trim.toInt,part.substring(idx+orderSplitAt.length,part.length).trim),end)
          }else{
            ((dfltSort,part),end)
          }
        }else{
          ((dfltSort,""),end)
        }
    }).sortBy(_._1._1).map(entry => {
      var clazz:Class[_ <: PlayRouter] = null
      try {
        clazz = Reflect.getClass[PlayRouter](entry._2, env.classLoader)
      } catch {
        case e: ClassNotFoundException =>
          throw conf.reportError("play.routes.enabled", "Router not found: " + entry._2)
      }
      val router = inject.instanceOf(clazz)
      if (entry._1._2.length > 0) {
        router.withPrefix(entry._1._2)
      } else {
        router
      }
    }) ++ Seq(
      inject.instanceOf[AssetsRoutes]
    )
  }

  override def handlerFor(request: RequestHeader): Option[Handler] = {
    includes.find(_.routes.isDefinedAt(request)).flatMap(_.handlerFor(request))
  }

  def getInclude = includes

  def routes: PartialFunction[RequestHeader, Handler] = Map.empty

  override def documentation: Seq[(String, String, String)] = includes.flatMap(_.documentation)

  override def withPrefix(prefix: String) = this

  def javaScriptReverseRouter():String = {
    //play.routing.JavaScriptReverseRouter.create()
    ""
  }
}
