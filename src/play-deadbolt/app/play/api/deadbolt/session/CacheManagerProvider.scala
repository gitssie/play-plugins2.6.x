package play.api.deadbolt.session

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Provider, Singleton}

import play.api.cache.AsyncCacheApi
import play.api.deadbolt.session.backend.{CacheApiDao, MemorySessionDAO}
import play.api.deadbolt.session.mgt._
import play.api.inject.{ApplicationLifecycle, Injector}
import play.api.{Configuration, Environment}
import play.cache.NamedCacheImpl
import play.utils.Reflect

import scala.concurrent.duration.Duration


/**
  * play.deadbolt.session{
  *   backend="memory|cacheName"
  *   factory=""
  *   timeout=""
  *   prefix=""
  * }
  */
@Singleton
class CacheManagerProvider @Inject() (injector: Injector,env: Environment, conf: Configuration, lifecycle: ApplicationLifecycle) extends Provider[SessionManager] {
  lazy val get: SessionManager = {
    val cfg = conf.getOptional[Configuration]("play.deadbolt.session").getOrElse(Configuration.empty)
    val configs = new Configs()
    configs.sessionPrefix = cfg.getOptional[String]("prefix").getOrElse("session.")
    configs.globalSessionTimeout = cfg.getOptional[Duration]("timeout").getOrElse(Duration(1,TimeUnit.HOURS)).toMillis

    val backend: SessionDAO = cfg.getOptional[String]("backend").map{
      case name if name == "memory" => new MemorySessionDAO()
      case name => {
        val namedCache = new NamedCacheImpl(name)
        val cacheApiKey = play.api.inject.bind[AsyncCacheApi].qualifiedWith(namedCache)
        val cachedApi = injector.instanceOf(cacheApiKey)
        new CacheApiDao(configs, cachedApi)
      }
    }.getOrElse(new MemorySessionDAO())

    val factoryCls = cfg.getOptional[String]("factory").map(fct => Reflect.getClass[SessionFactory](fct,env.classLoader)).getOrElse(classOf[SimpleSessionFactory])
    val factory = Reflect.createInstance[SessionFactory](factoryCls)

    val idGeneratorCls = cfg.getOptional[String]("idGenerator").map(fct => Reflect.getClass[SessionIdGenerator](fct,env.classLoader)).getOrElse(classOf[RandomSessionIdGenerator])
    val idGenerator = Reflect.createInstance[SessionIdGenerator](idGeneratorCls)

    val sessionManager = new DefaultSessionManager(configs,backend,factory,idGenerator)
    sessionManager
  }
}

