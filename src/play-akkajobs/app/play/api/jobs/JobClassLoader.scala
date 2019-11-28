package play.api.jobs

import java.util.UUID
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import play.api.inject.{ApplicationLifecycle, Injector}
import play.api.{Configuration, Environment}
import play.utils.Reflect

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}


/**
  * play.jobs{
  *     context{}
  *     enabled +={
  *       className = "com.github.test.ServiceMain"
  *       cronExpression = "0/10 * * * * ?"
  *     }
  *     disabled += "need disabled class name"
  * }
  * @param actorSystem
  * @param lifecycle
  * @param env
  * @param injector
  * @param config
  */
@Singleton
class JobClassLoader @Inject()(val actorSystem: ActorSystem,
                               val lifecycle: ApplicationLifecycle,
                               val env: Environment,
                               val injector: Injector,
                               val config: Configuration) {

  private val logger = LoggerFactory.getLogger(classOf[JobClassLoader])

  private val jobs = mutable.Map.empty[String,AbstractAkkaJob]

  private var executionContext:ExecutionContext = null

  private val jobLoaded = config.getOptional[Boolean]("play.jobs.loaded").getOrElse(true)

  lifecycle.addStopHook(() => Future.successful(onStop()))

  onStart(config)

  def onStop():Unit = jobs.foreach(_._2.stopJob())

  def onStart(conf: Configuration): Unit = {
    executionContext = conf.getOptional[Configuration]("play.jobs.context").map(_ => actorSystem.dispatchers.lookup("play.jobs.context")).getOrElse(actorSystem.dispatcher);

    logger.info("AkkaJob scheduler using execution context:{}",executionContext)

    if(conf.underlying.hasPath("play.jobs.enabled")) {
      val enabled = conf.underlying.getConfigList("play.jobs.enabled")
      val excludes = conf.getOptional[Seq[String]]("play.jobs.disabled").getOrElse(Seq.empty)
      enabled.asScala.filter(p => !excludes.exists(_ == p.getString("className"))).foreach(startJob(_))
    }
  }

  def startJob(cfg:Config):Unit = {
    val conf = Configuration(cfg)
    val className = conf.get[String]("className")
    try{
      val enabled = conf.getOptional[Boolean]("enabled").getOrElse(true)
      val cronExpr = conf.getOptional[String]("cronExpression").getOrElse("")
      val id = conf.getOptional[String]("id").getOrElse(UUID.randomUUID().toString)

      val clazz = Reflect.getClass[TaskScheduler](className,env.classLoader)

      startJob(id,clazz,cronExpr,enabled,null)

    }catch{
      case e:Throwable => logger.error("Error while initializing class: " + className, e)
    }
  }

  def startJob(id:String,clazz:Class[_ <: TaskScheduler],cronExpr:String,enabled:Boolean,msg:AnyRef):Unit = {
    logger.info("Trying to load class: {},id:{},enabled:{},cron:{}",clazz.getName,id,enabled+"",cronExpr)
    val existed = jobs.get(id)
    if(existed.isDefined){
      throw new JobException("Job already existed,id:"+id)
    }
    val task = injector.instanceOf(clazz)

    val job = new CronAkkaJob(id,actorSystem,executionContext,task,cronExpr,msg)
    jobs += id -> job

    if(enabled && jobLoaded) {
      job.scheduleJob()
    }
  }

  def runJob(id:String):Boolean = jobs.get(id).map(_.runNow()).map(_ => true).getOrElse(false)

  def stopJob(id:String):Boolean = jobs.get(id).map(_.stopJob()).map(_ => true).getOrElse(false)

  def deleteJob(id:String):Boolean = jobs.remove(id).map(_.stopJob()).map(_ => true).getOrElse(false)

  def getJob(id:String) = jobs.get(id)

  def getJobs():Seq[AbstractAkkaJob] = jobs.values.toSeq
}