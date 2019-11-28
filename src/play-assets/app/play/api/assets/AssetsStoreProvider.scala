package play.api.assets

import javax.inject.{Inject, Provider}

import play.api.inject.Injector
import play.api.{Configuration, Environment}
import play.utils.Reflect

class AssetsStoreProvider  @Inject() (inject:Injector,env:Environment,conf:Configuration) extends Provider[AssetsStore]{
  val defaultCls = Map("file" -> classOf[FileAssetsStore])
  override def get():AssetsStore = conf.getOptional[String]("play.assets.store.class").map(clazz => {
    try{
      defaultCls.find(_._1 == clazz).map(c => inject.instanceOf(c._2)).getOrElse{
        inject.instanceOf(Reflect.getClass[AssetsStore](clazz,env.classLoader))
      }
    }catch {
      case e: ClassNotFoundException =>
        throw conf.reportError("play.assets.store.class", "Assets store not found: " + clazz)
    }
  }).getOrElse(null)
}
