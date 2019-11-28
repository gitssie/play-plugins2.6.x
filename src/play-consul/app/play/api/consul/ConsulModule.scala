package play.api.consul

import java.net.URI
import javax.inject.{Inject, Provider, Singleton}

import com.ecwid.consul.v1.ConsulClient
import play.api.inject.{ApplicationLifecycle, Module}
import play.api.{Configuration, Environment}

import scala.concurrent.ExecutionContextExecutor


class ConsulModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[ConsulClient].toProvider[ConsulProvider]
  )
}

/**
  * consul config
  *
  * play.consul.servers="http://192.168.33.10:8500"
  * play.consul.session-timeout=60s
  * play.consul.connect-timeout=10s
  *
  */
@Singleton
class ConsulProvider @Inject() (conf:Configuration,applicationLifecycle: ApplicationLifecycle,implicit val ex:ExecutionContextExecutor) extends Provider[ConsulClient] {
  override def get():ConsulClient = {
    val url = new URI(conf.get[String]("play.consul.servers"))
    val client = new ConsulClient(url.getScheme + "://" + url.getHost,url.getPort)
    client
  }
}
