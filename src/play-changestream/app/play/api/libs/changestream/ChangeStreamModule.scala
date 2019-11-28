package play.api.libs.changestream

import javax.inject.{Inject, Singleton}

import play.api.inject.{ApplicationLifecycle, Module}
import play.api.{Configuration, Environment}

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ChangeStreamLoader @Inject() (val cs:ChangeStream,val applicationLifecycle: ApplicationLifecycle,implicit val ec:ExecutionContext) {
  Future{cs.connect()}
  applicationLifecycle.addStopHook(() => Future{cs.disconnect()})
}

class ChangeStreamModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[ChangeStreamLoader].toSelf.eagerly()
  )
}