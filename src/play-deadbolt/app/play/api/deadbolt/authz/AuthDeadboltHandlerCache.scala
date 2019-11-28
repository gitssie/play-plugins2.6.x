package play.api.deadbolt.authz

import javax.inject.{Inject, Singleton}

import be.objectify.deadbolt.scala.{DeadboltHandler, HandlerKey}
import be.objectify.deadbolt.scala.cache.HandlerCache
import be.objectify.deadbolt.scala.filters.SimpleHandlerKey


@Singleton
class AuthDeadboltHandlerCache @Inject() (handler: DeadboltHandler) extends HandlerCache {

  val handlers: Map[Any, DeadboltHandler] = Map(HandlerKeys.defaultHandler -> handler,
    HandlerKeys.altHandler -> handler,
    HandlerKeys.userlessHandler -> handler)

  override def apply(): DeadboltHandler = handler

  override def apply(handlerKey: HandlerKey): DeadboltHandler = handlers.get(handlerKey).getOrElse(handler)
}

object HandlerKeys {

  val defaultHandler = Key("defaultHandler")
  val altHandler = Key("altHandler")
  val userlessHandler = Key("userlessHandler")

  case class Key(name: String) extends HandlerKey

}