package play.api.config.cl

import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import com.ecwid.consul.v1.{ConsulClient, QueryParams}
import com.google.common.collect.Maps
import com.typesafe.config.{Config, ConfigFactory}
import play.api.Configuration
import play.api.config.{ConfigProvider, ConfValue}
import play.api.inject.ApplicationLifecycle
import play.libs.Json

import scala.collection.JavaConverters._
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class ConsulConfigProvider @Inject() (client:ConsulClient,conf:Configuration,applicationLifecycle: ApplicationLifecycle,actorSystem: ActorSystem,implicit val ec:ExecutionContext)extends ConfigProvider{
  val basePath = {
    val path = conf.get[String]("play.config.cl.base-path")
    if(path.charAt(0) == '/'){
      path.substring(1,path.length)
    }else{
      path
    }
  }
  val waitTime = conf.getOptional[Duration]("play.config.cl.wait-time").getOrElse(Duration(30,TimeUnit.SECONDS)).toSeconds

  val configs = Maps.newHashMap[String,ConfValue]()
  val charset = Charset.forName("UTF-8")
  var onChanged:(Option[String],Config) => Unit = _

  val qpBuild = QueryParams.Builder.builder().setWaitTime(waitTime)

  @volatile var configIndex:Long = 0
  @volatile var isOpened = true

  override def bind(event: (Option[String],Config) => Unit): Unit = onChanged = event

  override def get: Config = {
    listener
    buildConfigs(false)
    toConfig
  }



  def listener:Unit = {
    val future = Future(client.getKVValue(basePath,qpBuild.setIndex(configIndex).build()))
    future.onComplete{
      case Failure(t) if isOpened => {
        actorSystem.scheduler.scheduleOnce(FiniteDuration.apply(waitTime,TimeUnit.SECONDS))(listener)
      }
      case Success(v) if isOpened => {
        if(v.getConsulIndex > configIndex && configIndex > 0){
          Future{
            buildConfigs(true)
          }
        }
        configIndex = v.getConsulIndex
        listener
      }
      case _ =>
    }
  }

  def buildConfigs(fire:Boolean): Unit ={
    configs.clear()
    client.getKVValues(basePath).getValue.asScala.foreach{child =>
      configs.put(getKey(child.getKey),getJsonValue(child.getDecodedValue(charset).getBytes()))
    }
    if(fire){
      onChanged(None,toConfig)
    }
  }

  def getJsonValue(data:Array[Byte]): ConfValue ={
    Json.fromJson(Json.parse(data),classOf[ConfValue])
  }

  def toConfig:Config = {
    val maps = Maps.newHashMap[String,String]()
    configs.asScala.foreach{
      case (k,v) => maps.put(k,v.value)
    }
    ConfigFactory.parseMap(maps)
  }

  def getKey(key:String):String = key.substring(basePath.length + 1,key.length)

  def close:Unit = isOpened = false

  applicationLifecycle.addStopHook(() => Future.successful(close))

  override def put(key: String, v: ConfValue): Unit = {}

  override def remove(key: String): Unit = {}

  override def get(key: String) = configs.get(key)

  override def list = configs.values()

  override def checkExists(key: String) = get(key) != null
}
