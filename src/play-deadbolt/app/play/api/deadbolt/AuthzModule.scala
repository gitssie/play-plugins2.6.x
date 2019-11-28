package play.api.deadbolt

import be.objectify.deadbolt.scala.DeadboltHandler
import be.objectify.deadbolt.scala.cache.HandlerCache
import play.api.deadbolt.authz.{AuthDeadboltHandler, AuthDeadboltHandlerCache, DefaultAuthz, DefaultPasswordService, DefaultSecurityManager, PasswordService, SecurityManager}
import play.api.deadbolt.realm.{Realm, RealmProvider}
import play.api.deadbolt.session.CacheManagerProvider
import play.api.deadbolt.session.mgt.SessionManager
import play.api.inject.Module
import play.api.{Configuration, Environment}


class AuthzModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[Realm].toProvider[RealmProvider],
    bind[PasswordService].to[DefaultPasswordService],
    bind[SecurityManager].to[DefaultSecurityManager],
    bind[SessionManager].toProvider[CacheManagerProvider],
    bind[Authz].to[DefaultAuthz],
    bind[DeadboltHandler].to[AuthDeadboltHandler],
    bind[HandlerCache].to[AuthDeadboltHandlerCache]
  )
}
