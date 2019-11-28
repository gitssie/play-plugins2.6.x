package play.api.ddsl.cl

import java.util
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.{Inject, Singleton}

import akka.actor.{ActorSystem, Cancellable}
import com.ecwid.consul.v1.agent.model.Check.CheckStatus
import com.ecwid.consul.v1.{ConsulClient, QueryParams}
import com.ecwid.consul.v1.agent.model.{NewCheck, NewService}
import com.google.common.collect.Lists
import play.api.ddsl.{DdslClientProvider, ServiceInstance, ServiceType}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

@Singleton
class ConsulDdslProvider @Inject()(client: ConsulClient,actorSystem: ActorSystem,implicit val ec:ExecutionContext) extends DdslClientProvider{
  val services = mutable.Map.empty[String,ServiceInstance]
  val ttl = FiniteDuration.apply(30,TimeUnit.SECONDS)
  var beginCheck = new AtomicLong(0)
  var task:Option[Cancellable] = None

  override def serviceUp(sv: ServiceInstance): Unit = registerService(sv)

  override def serviceDown(sv: ServiceInstance): Unit = unregisterService(sv)

  override def serviceUpdate(sv: ServiceInstance): Unit = registerService(sv)

  override def queryForNames: util.List[String] = Lists.newArrayList(client.getAgentServices.getValue.keySet())

  override def queryForInstance(name: String, id: String): ServiceInstance = queryForInstances(name).asScala.find(_.id == id).getOrElse(null)

  override def queryForInstances(name: String): util.List[ServiceInstance] = {

    val services = client.getHealthServices(name,false, QueryParams.Builder.builder().build()).getValue.asScala
    services.map{sv =>
      val service = sv.getService

      val enabled = sv.getChecks.asScala.find(c => c.getStatus == CheckStatus.PASSING).map(_ => true).getOrElse(false)
      new ServiceInstance(
        service.getService,
        service.getId,
        service.getAddress,
        service.getPort,
        None,
        System.currentTimeMillis(),
        service.getTags,
        ServiceType.DYNAMIC,
        "",
        true
      )
    }
  }.asJava

  override def registerService(sv: ServiceInstance): Unit = {
    services += sv.getId -> sv

    val service = new NewService()
    service.setName(sv.getName)
    service.setId(sv.getId)
    service.setAddress(sv.getId)
    service.setPort(sv.getPort)
    service.setEnableTagOverride(true)
    service.setTags(sv.getTags)

    val check = new NewService.Check()
    if(sv.getServiceType == ServiceType.DYNAMIC) check.setDeregisterCriticalServiceAfter("1m")
    check.setTtl("60s")
    if(sv.isEnabled) check.setStatus("passing") else check.setStatus("critical")

    service.setCheck(check)
    client.agentServiceRegister(service)

    val last = beginCheck.get()
    if(last == 0 && beginCheck.compareAndSet(last,System.currentTimeMillis())){
      val tk = actorSystem.scheduler.schedule(ttl,ttl)(updateCheck)
      task = Some(tk)
    }
  }

  def updateCheck:Unit = {
    services.foreach{
      case (key,sv) => Try {
        val check = new NewCheck()
        check.setId("service:" + sv.getId)
        check.setName("Scheduler")
        check.setDeregisterCriticalServiceAfter("1m")
        check.setTtl("60s")
        check.setStatus("passing")
        check.setNotes("On Scheduler Pass")
        client.agentCheckRegister(check)
      }
    }
  }

  override def unregisterService(inst: ServiceInstance): Unit = {
    services -= inst.getId
    client.agentServiceDeregister(inst.getId)
  }

  override def updateService(sv: ServiceInstance): Unit = registerService(sv)

  override def close: Unit = task.map(_.cancel())
}
