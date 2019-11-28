package play.api.jobs

import java.text.ParseException

import akka.actor.ActorSystem
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime

import scala.concurrent.ExecutionContext


class CronAkkaJob(id:String,
                  actorSystem: ActorSystem,
                  executor: ExecutionContext,
                  scheduler: TaskScheduler,
                  cronExp: String,
                  msg:AnyRef)
  extends AbstractAkkaJob(id,actorSystem, executor) {

  setCronExpression({
    if(StringUtils.isAllEmpty(cronExp)){
      parseCronInAnnotated(scheduler.getClass)
    }else{
      parseCronInCronExpression(cronExp.trim)
    }
  })

  override def runInternal(): Unit = scheduler.runInternal(getNextFireDate,msg)

  override def stopInternalJob(): Unit = scheduler.stopInternal(DateTime.now(),msg)

  @throws[JobException]
  def parseCronInCronExpression(annoCronExpression: String): CronExpression = {
    try {
      new CronExpression(annoCronExpression)
    }catch {
      case e: ParseException =>
        throw new JobException(e.getMessage,e)
    }
  }

  @throws[JobException]
  def parseCronInAnnotated(clazz: Class[_]): CronExpression = {
    val annotation = clazz.getAnnotation(classOf[AkkaJob])
    val akkaJob = annotation.asInstanceOf[AkkaJob]
    val annoCronExpression = akkaJob.cronExpression.trim
    parseCronInCronExpression(annoCronExpression)
  }
}