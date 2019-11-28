package play.api.ddsl

import java.util
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.{Inject, Provider, Singleton}

import com.google.common.collect.{Lists, Maps}
import com.typesafe.config.{Config, ConfigList, ConfigObject, ConfigValue}
import play.api.{Configuration, Environment}
import play.api.inject.{ApplicationLifecycle, Injector}
import play.utils.Reflect

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

trait DdslClientProvider {

  def serviceUp(service:ServiceInstance): Unit

  def serviceDown(service:ServiceInstance):Unit

  def serviceUpdate(service:ServiceInstance):Unit

  def queryForNames:util.List[String]

  def queryForInstance(name:String,id:String):ServiceInstance

  def queryForInstances(name:String):util.List[ServiceInstance]

  def registerService(inst:ServiceInstance)

  def unregisterService(inst:ServiceInstance)

  def updateService(inst:ServiceInstance)

  def queryForAvailable(name: String): util.List[ServiceInstance] = queryForInstances(name).asScala.filter(_.isEnabled).asJava

  def queryForAddress(name: String): util.List[String] = queryForInstances(name).asScala.filter(_.isEnabled).map(i =>
    i.getAddress + ":" + (if(i.getPort > 0) i.getPort else i.getSslPort)
  ).asJava

  def close:Unit
}

trait DdslClient extends DdslClientProvider{
  def getServiceInstance:ServiceInstance
}

@Singleton
class CachedDdslClient @Inject() (client:DdslClientProvider,conf:Configuration,applicationLifecycle: ApplicationLifecycle,implicit val ec:ExecutionContext) extends DdslClient {

  val instances: util.Map[String,util.List[ServiceInstance]] = Maps.newHashMap()

  var lastModified:AtomicLong = new AtomicLong(0)

  val cacheTime:Long = conf.getOptional[Duration]("play.ddsl.client.cache-time").getOrElse(Duration.apply(10,TimeUnit.SECONDS)).toMillis

  override def serviceUp(service:ServiceInstance): Unit = client.serviceUp(service)

  override def serviceDown(service:ServiceInstance): Unit = client.serviceDown(service)

  override def serviceUpdate(service:ServiceInstance): Unit = client.serviceUpdate(service)

  override def getServiceInstance = serviceInstance.map(s => queryForInstance(s.getName,s.getId)).getOrElse(null)

  override def queryForNames: util.List[String] = client.queryForNames

  override def queryForInstance(name: String, id: String): ServiceInstance =  queryForInstances(name).asScala.find(_.getId == id).getOrElse(null)

  override def queryForInstances(name: String): util.List[ServiceInstance] = {
    val last = lastModified.get()
    var insts = instances.get(name)
    val isEmpty = insts == null || insts.size() == 0

    if(isEmpty || System.currentTimeMillis() - last > cacheTime){
      if(lastModified.compareAndSet(last,System.currentTimeMillis())){
        Future{
          instances.put(name,client.queryForInstances(name))
        }
      }
      if(isEmpty){
        this.synchronized{
          try{
            insts = client.queryForInstances(name)
            instances.put(name,insts)
          }catch{
            case _:Throwable=>
          }
        }
        insts
      }else{
        insts
      }
    }else{
      insts
    }
  }

  override def registerService(inst: ServiceInstance): Unit = client.registerService(inst)

  override def unregisterService(inst: ServiceInstance): Unit = client.unregisterService(inst)

  override def updateService(inst: ServiceInstance): Unit = client.updateService(inst)

  /**
    * play.ddsl.service.name
    * play.ddsl.service.id
    * play.ddsl.service.address
    * play.ddsl.service.port
    * play.ddsl.service.ssl-port
    * play.ddsl.service.type
    * play.ddsl.service.uri
    * play.ddsl.service.enabled
    */
  val serviceInstance:Option[ServiceInstance] = conf.getOptional[Configuration]("play.ddsl.service").map(f =>{
    val service = new ServiceInstance(
      f.get[String]("name"),
      f.getOptional[String]("id").getOrElse(UUID.randomUUID().toString),
      f.getOptional[String]("address").getOrElse(conf.getOptional[String]("play.server.http.address").getOrElse("")),
      f.getOptional[Int]("port").getOrElse(conf.getOptional[Int]("play.server.http.port").getOrElse(0)),
      f.getOptional[Int]("ssl-port").orElse(conf.getOptional[Int]("play.server.https.port")),
      System.currentTimeMillis(),
      Lists.newArrayList(f.getOptional[String]("tags").getOrElse("").split(",") :_*),
      f.getOptional[String]("type").map(tp => ServiceType.withName(tp.toLowerCase)).getOrElse(ServiceType.DYNAMIC),
      f.getOptional[String]("uri-spec").getOrElse("{scheme}://{address}:{port}"),
      f.getOptional[Boolean]("enabled").getOrElse(true)
    )
    Some(service)
  }).getOrElse(None)

  override def close: Unit = {
    serviceInstance.map(serviceDown(_))
    client.close
  }

  //------initialize----------

  serviceInstance.map(serviceUp(_))  //register service

  applicationLifecycle.addStopHook(() => Future.successful(close)) //close

}

/**
  *
  * play.ddsl.client.static{
  * netapi += {
  *   port = 80
  *   ssl-port = 443
  *   address = "127.0.0.1"
  *   enabled = true
  * }
  * netapi += {
  *   port = 80
  *   address = "127.0.0.2"
  *  }
  * }
  */
@Singleton
class StaticDdslClient @Inject() (configuration :Configuration) extends DdslClientProvider {
  val services = mutable.Map.empty[String,ServiceInstance]

  override def serviceUp(sv: ServiceInstance): Unit = registerService(sv)

  override def serviceDown(sv: ServiceInstance): Unit = unregisterService(sv)

  override def serviceUpdate(sv: ServiceInstance): Unit = updateService(sv)

  override def queryForNames: util.List[String] = services.map(_._2.getName).toList.asJava

  override def queryForInstance(name: String, id: String): ServiceInstance = services.find{case (k,v) => v.getName == name && v.getId == id}.map(_._2).getOrElse(null)

  override def queryForInstances(name: String): util.List[ServiceInstance] = services.filter{case (k,v) => v.getName == name}.map(_._2).toList.asJava

  override def registerService(sv: ServiceInstance): Unit = services += (sv.getId -> sv)

  override def unregisterService(sv: ServiceInstance): Unit = services -= sv.getId

  override def updateService(sv: ServiceInstance): Unit = services += (sv.getId -> sv)

  override def close: Unit = services.clear()

  def initialize:Unit = {

    val extractServiceOne = (name:String,value: ConfigObject) => {
        val f = Configuration(value.toConfig)
        val sv = new ServiceInstance(
          name,
          f.getOptional[String]("id").getOrElse(UUID.randomUUID().toString),
          f.get[String]("address"),
          f.getOptional[Int]("port").getOrElse(0),
          f.getOptional[Int]("ssl-port"),
          System.currentTimeMillis(),
          Lists.newArrayList(f.getOptional[String]("tags").getOrElse("").split(",") :_*),
          f.getOptional[String]("type").map(tp => ServiceType.withName(tp.toLowerCase)).getOrElse(ServiceType.DYNAMIC),
          f.getOptional[String]("uri-spec").getOrElse("{scheme}://{address}:{port}"),
          f.getOptional[Boolean]("enabled").getOrElse(true)
        )
        registerService(sv)
    }

    val extractServiceList = (name:String,value:ConfigList) => {
      value.asScala.filter(_.isInstanceOf[ConfigObject]).foreach(c => extractServiceOne(name,c.asInstanceOf[ConfigObject]))
    }

    configuration.getOptional[Configuration]("play.ddsl.client.static").map{conf =>
      conf.underlying.entrySet().asScala.foreach{
        case entry => entry.getValue match {
          case cfg:ConfigObject => extractServiceOne(entry.getKey,cfg)
          case cfg:ConfigList => extractServiceList(entry.getKey,cfg)
          case _ =>
        }
      }
    }
  }

  initialize
}

@Singleton
private [ddsl] class ClientProvider @Inject() (conf:Configuration,inject:Injector,env:Environment) extends Provider[DdslClientProvider] {
  override def get():DdslClientProvider = {
    val clazzOpt = conf.getOptional[String]("play.ddsl.provider").map(clazzName => Reflect.getClass[DdslClientProvider](clazzName,env.classLoader))
    inject.instanceOf(clazzOpt.getOrElse(classOf[StaticDdslClient]))
  }
}