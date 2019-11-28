package play.api.ddsl

import play.api.inject.Module
import play.api.{Configuration, Environment}

class DdslModule extends Module{
  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[DdslClient].to[CachedDdslClient],
    bind[DdslClientProvider].toProvider[ClientProvider]
  )
}


