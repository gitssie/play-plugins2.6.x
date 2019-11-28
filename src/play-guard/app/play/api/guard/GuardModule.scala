package play.api.guard

import play.api.{Configuration, Environment}
import play.api.inject.Module

class GuardModule extends Module{
  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[GuardFilter].toSelf
  )
}
