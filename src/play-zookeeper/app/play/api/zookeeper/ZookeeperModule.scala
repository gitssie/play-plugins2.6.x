package play.api.zookeeper

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Provider, Singleton}

import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.RetryNTimes
import play.api.inject.{ApplicationLifecycle, Module}
import play.api.{Configuration, Environment}

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContextExecutor, Future}

class ZookeeperModule extends Module{
  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[CuratorFramework].toProvider[CuratorProvider]
  )
}


/**
  * zookeeper config
  *
  * play.zookeeper.curator.servers="localhost:2181,localhost:2181"
  * play.zookeeper.curator.session-timeout=60s
  * play.zookeeper.curator.connect-timeout=10s
  * play.zookeeper.curator.retry-count=1000
  * play.zookeeper.curator.retry-sleep=10s
  *
  */
@Singleton
class CuratorProvider @Inject() (conf:Configuration,applicationLifecycle: ApplicationLifecycle,implicit val ex:ExecutionContextExecutor) extends Provider[CuratorFramework]{

  override def get(): CuratorFramework = {
    val server = conf.get[String]("play.zookeeper.curator.servers")
    val sessionTimeout = conf.getOptional[Duration]("play.zookeeper.curator.session-timeout").getOrElse(Duration(60,TimeUnit.SECONDS))
    val connetTimeout = conf.getOptional[Duration]("play.zookeeper.curator.connect-timeout").getOrElse(Duration(60,TimeUnit.SECONDS))
    val retryCount = conf.getOptional[Int]("play.zookeeper.curator.retry-count").getOrElse(30)
    val retrySleep = conf.getOptional[Duration]("play.zookeeper.curator.retry-sleep").getOrElse(Duration(10,TimeUnit.SECONDS))

    val client = CuratorFrameworkFactory.newClient(server,
      sessionTimeout.toMillis.toInt,
      connetTimeout.toMillis.toInt,
      new RetryNTimes(retryCount,retrySleep.toMillis.toInt))

    client.start()
    client.blockUntilConnected(10,TimeUnit.SECONDS)
    client
  }

  applicationLifecycle.addStopHook{() =>
    Future.successful(get().close())
  }
}