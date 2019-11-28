package play.api.ddsl.zk

import java.util
import javax.inject.{Inject, Singleton}

import com.google.common.collect.Lists
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.x.discovery.details.JsonInstanceSerializer
import org.apache.curator.x.discovery.{ServiceDiscovery, ServiceDiscoveryBuilder, ServiceType, UriSpec, ServiceInstance => CuratorServiceInstance}
import play.api.Configuration
import play.api.ddsl.{DdslClientProvider, ServiceInstance}
import play.api.inject.ApplicationLifecycle

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContextExecutor, Future}

@Singleton
class ZookeeperDdslProvider @Inject() (client:CuratorFramework,conf:Configuration,applicationLifecycle: ApplicationLifecycle,implicit val ex:ExecutionContextExecutor) extends  DdslClientProvider {

  override def serviceUp(service:ServiceInstance): Unit = serviceDiscovery.registerService(service)

  override def serviceDown(service:ServiceInstance):Unit = serviceDiscovery.unregisterService(service)

  override def serviceUpdate(service:ServiceInstance):Unit = serviceDiscovery.updateService(service)

  override  def queryForNames = new util.ArrayList[String](serviceDiscovery.queryForNames())

  override def queryForInstance(name:String,id:String):ServiceInstance = serviceDiscovery.queryForInstance(name,id)

  override def queryForInstances(name:String):util.List[ServiceInstance] = serviceDiscovery.queryForInstances(name)

  lazy val serviceDiscovery: ServiceDiscovery[InstanceDetails] = {
    val basePath = conf.get[String]("play.ddsl.base-path")
    val serializer = new JsonInstanceSerializer[InstanceDetails](classOf[InstanceDetails],false)
    val serviceDiscovery = ServiceDiscoveryBuilder.builder(classOf[InstanceDetails])
      .client(client)
      .serializer(serializer)
      .basePath(basePath)
      .watchInstances(true)
      .build

    Future(serviceDiscovery.start())

    serviceDiscovery
  }

  def getServiceDiscovery:ServiceDiscovery[InstanceDetails] = serviceDiscovery

  override def registerService(inst: ServiceInstance): Unit = serviceDiscovery.registerService(inst)

  override def unregisterService(inst: ServiceInstance): Unit = serviceDiscovery.unregisterService(inst)

  override def updateService(inst: ServiceInstance): Unit = serviceDiscovery.updateService(inst)

  implicit def toCuratorService(service:ServiceInstance):CuratorServiceInstance[InstanceDetails]= {
    val lb = CuratorServiceInstance.builder[InstanceDetails]()

    lb.name(service.getName)
        .id(service.id)
        .address(service.address)
        .port(service.getPort)

    if(service.getSslPort.isDefined) {
      lb.sslPort(service.getSslPort.get)
    }
    lb.registrationTimeUTC(service.registrationTimeUTC)
      .serviceType(ServiceType.valueOf(service.serviceType.toString))
      .payload(new InstanceDetails(service.getTags))
      .enabled(service.enabled)
      .uriSpec(new UriSpec(service.getUriSpec))

    lb.build()
  }

  implicit def toService(service:CuratorServiceInstance[InstanceDetails]):ServiceInstance = {
    new ServiceInstance(
      service.getName,
      service.getId,
      service.getAddress,
      if(service.getPort == null) 0 else service.getPort,
      if(service.getSslPort == null) None else Option(service.getSslPort),
      service.getRegistrationTimeUTC,
      if(service.getPayload == null) Lists.newArrayList() else service.getPayload.getTags(),
      play.api.ddsl.ServiceType.withName(service.getServiceType.name()),
      service.buildUriSpec(),
      service.isEnabled
    )
  }

  implicit def toServices(services:util.Collection[CuratorServiceInstance[InstanceDetails]]):util.List[ServiceInstance] = Lists.newArrayList(services.asScala.map(toService(_)).asJava)

  override def close: Unit = serviceDiscovery.close()
}

class InstanceDetails(var tags: util.List[String]){

  def this() = this(Lists.newArrayListWithCapacity(6))

  def getTags():util.List[String] = tags

  def setTags(tags:util.List[String]) = this.tags = tags
}
