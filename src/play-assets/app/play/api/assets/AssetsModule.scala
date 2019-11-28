package play.api.assets

import play.api.inject.Module
import play.api.{Configuration, Environment}

class AssetsModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[AssetsStore].toProvider[AssetsStoreProvider]
  )
}