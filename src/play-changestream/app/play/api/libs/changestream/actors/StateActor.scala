package play.api.libs.changestream.actors

import java.util.concurrent.TimeUnit

import org.slf4j.LoggerFactory
import play.api.libs.changestream.ChangeStream

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success}

/**
  * 如果出现不可遇见的错误,直接停止复制,需要人工来进行重启
  * 或者可以考虑隔10分钟,重新连接启动
  * @param cs
  */
class StateActor(cs:ChangeStream) extends SyncActor {
  protected val log = LoggerFactory.getLogger(getClass)

  val recoverTime = cs.conf.getOptional[Int]("mysql.recover").getOrElse(10000)

  def receive = {
    case Success(_) => {
      cs.saveBinlogPosition
    }
    case Failure(t) => {
      cs.disconnect() match {
        case true =>
          log.info("Paused.",t)
          cs.system.scheduler.scheduleOnce(Duration.create(recoverTime,TimeUnit.MILLISECONDS))(cs.connect)(cs.ec)
        case false =>
          log.warn("Pause failed, perhaps we are already paused?",t)
      }
    }
  }
}