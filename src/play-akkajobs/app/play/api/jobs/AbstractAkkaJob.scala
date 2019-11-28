package play.api.jobs

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Cancellable}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

abstract class AbstractAkkaJob (val id:String,val actorSystem: ActorSystem,val executor: ExecutionContext) extends Runnable {
  protected val LOGGER = LoggerFactory.getLogger(classOf[AbstractAkkaJob])
  /**
    * The Cron-Expression of this job.
    */
  var cronExpression: CronExpression = null

  /**
    * The current state of the job
    */
  private var runState =  EJobRunState.STOPPED
  /**
    * Restart this job when it failed ?
    */
  private var restartOnFail = true
  /**
    * The date when the job will be next fired
    */
  private var nextFireDate:DateTime = null
  /**
    * When the job runs this is the cancallable
    */
  private var cancellable:Cancellable = null

  def getId():String = id

  /**
    * Schedules the job
    */
  def scheduleJob(): Unit = {
    if (EJobRunState.DISABLED == runState) {
      LOGGER.info("Runstate for the job: " + this.getClass.getName + " is " + EJobRunState.DISABLED + " not going to run")
      return
    }
    if (cronExpression == null) {
      LOGGER.error("No Cronexpression set at: " + this.getClass.getName)
      return
    }
    val now = new DateTime
    var nextInterval = 0L
    if (nextFireDate == null && isSubmittedImmediately == true) {
      nextFireDate = now
      nextInterval = 0
    } else {
      nextFireDate = cronExpression.nextTimeAfter(now)
      nextInterval = nextFireDate.getMillis - now.getMillis
    }

    val duration = Duration.create(nextInterval, TimeUnit.MILLISECONDS)
    runState = EJobRunState.SCHEDULED
    cancellable = actorSystem.scheduler.scheduleOnce(duration, this)(executor)

    if (LOGGER.isDebugEnabled) {
      LOGGER.debug(this.getClass.getName + " job is running again in: " + duration.toString + " @ " + nextFireDate)
    }
  }

  def runNow():Unit = {
    if (EJobRunState.RUNNING == runState) {
      return
    }
    try {
      runState = EJobRunState.RUNNING
      runInternal()
      runState = EJobRunState.STOPPED
    } catch {
      case e: Exception =>
        LOGGER.error("An error happend in the internal implementation of the job: " + this.getClass.getName, e)
        runState = EJobRunState.ERROR
    }
  }


  override def run(): Unit = { // check if the state is not running
    if (EJobRunState.RUNNING == runState) {
      LOGGER.warn(this.getClass.getName + " Job not started because it is still in run mode.")
      scheduleJob()
      return
    }
    if (LOGGER.isDebugEnabled) {
      LOGGER.debug(this.getClass.getName + " job is going to run.")
    }
    try {
      runState = EJobRunState.RUNNING
      runInternal()
      runState = EJobRunState.STOPPED
    } catch {
      case e: Exception =>
        LOGGER.error("An error happend in the internal implementation of the job: " + this.getClass.getName, e)
        runState = EJobRunState.ERROR
        if (restartOnFail == false) {
          runState = EJobRunState.KILLED
          if (LOGGER.isDebugEnabled) {
            LOGGER.debug("Will not restart the job: " + this.getClass.getName)
          }
          return
        }
    }
    scheduleJob()
  }

  def setRestartOnFail(restartOnFail: Boolean): Unit = this.restartOnFail = restartOnFail

  /**
    * Here you can implement the actual doing of the job
    */
  def runInternal(): Unit

  /**
    * This is called when the {@link AbstractAkkaJob#stopJob()} is called.
    * You can override this method in your implementation if you need to.
    */
  def stopInternalJob(): Unit = {
    // noop
  }

  /**
    * Stops the current job by calling the {@link Cancellable}
    */
  def stopJob(): Unit = {
    if (cancellable == null) {
        return
    }
    if (cancellable.isCancelled) return
    // some internal clean up by the job needed ?
    stopInternalJob()
    LOGGER.info("Stop for job: " + this.getClass.getName + " called.")
    cancellable.cancel
  }

  def getCronExpression: CronExpression = cronExpression

  def getRunState: EJobRunState.State = runState

  def isRestartOnFail: Boolean = restartOnFail

  def setCronExpression(cronExpression: CronExpression): Unit = this.cronExpression = cronExpression

  def getActorSystem: ActorSystem = actorSystem

  def setRunState(runState: EJobRunState.State): Unit = this.runState = runState

  def getNextFireDate: DateTime = nextFireDate

  def setNextFireDate(nextFireDate: DateTime): Unit = this.nextFireDate = nextFireDate

  /**
    * When set to true the job is submitted immediately
    *
    * @return true when the job should be executed when it is constructed the first time, false when not.
    */
  def isSubmittedImmediately = false
}