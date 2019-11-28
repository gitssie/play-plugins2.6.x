package play.api.config

import java.util.function.Consumer
import javax.inject.{Inject, Provider, Singleton}

import com.typesafe.config.Config
import play.api.inject.{ApplicationLifecycle, Injector, Module}
import play.api.{Configuration, Environment, Logger}
import play.utils.Reflect

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class ConfigModule extends Module{
  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[Configuration].qualifiedWith("conf").toProvider[DynamicConfigurationProvider],
    bind[play.Configuration].qualifiedWith("conf").toProvider[DynamicJavaConfigProvider],
    bind[Config].qualifiedWith("conf").toProvider[DynamicConfigProvider]
  )
}

/**
  * config:
  * play.config.provider=class
  */
@Singleton
class DynamicConfigurationProvider @Inject() (configEventBus: ConfigManager) extends Provider[Configuration] {
  override def get():Configuration = configEventBus.getConf.getWrappedConfiguration
}

@Singleton
class DynamicConfigProvider @Inject() (configEventBus: ConfigManager) extends Provider[Config]{
  override def get():Config = configEventBus.getConf.underlying()
}

@Singleton
class DynamicJavaConfigProvider @Inject() (configEventBus: ConfigManager) extends Provider[play.Configuration]{
  override def get():play.Configuration = configEventBus.getConf
}

/**
  * conf.on("play.ws.timeout",v => {
  *   v.getInt
  * })
  *
  */
@Singleton
class ConfigManager @Inject() (env:Environment,
                                conf:Configuration,
                                inject:Injector,
                                applicationLifecycle: ApplicationLifecycle,
                                implicit val ex:ExecutionContext) extends ConfigProvider{
  type Listener = (String) => Unit
  private val logger = Logger(classOf[ConfigManager])
  private val listeners = mutable.Map.empty[String,Listener]

  def on(prefix:String,onChange:Listener):Unit = {
    listeners.put(prefix,onChange)
  }

  def on(prefix:String, onChange: Consumer[String]):Unit = {
    val func:Listener = (value:String) => {
      onChange.accept(value)
    }
    on(prefix,func)
  }

  def unbind(prefix:String):Unit = {
    listeners.remove(prefix)
  }

  def fire(opt:String,value: String):Unit = fire(Option(opt),value)

  def fire(opt:Option[String],value: String):Unit = opt.map{fullPath =>
    listeners.foreach{
      case (key,onChange) if fullPath.startsWith(key)=> {
        logger.info("fire event on key:"+fullPath+",trigger:"+onChange.hashCode())
        Try(onChange(value)) match {
          case Success(_) =>
          case Failure(t) => logger.error("fire event trigger by key:"+fullPath,t)
        }
      }
      case _ =>
    }
  }

  lazy val provider : ConfigProvider = {
    conf.getOptional[String]("play.config.provider").filter(_.trim.length > 0).map {pClass =>
      try{
        val clazz = Reflect.getClass[ConfigProvider](pClass,env.classLoader)
        val provider = inject.instanceOf(clazz)
        provider
      }catch{
        case e:ClassNotFoundException => throw conf.reportError("play.config.provider","Class ["+pClass+"]NotFound",Some(e))
        case t:Throwable => throw t
      }
    }.getOrElse{
      new EmptyConfigProvider()
    }
  }

  override def bind(onChange: (Option[String], Config) => Unit): Unit = provider.bind((opt,config) =>{
    onChange(opt,config)
    opt.foreach {key =>
      Future {
        fire(key, key)
      }
    }
  })

  override def get: Config = provider.get

  lazy val dynamicConfig = new DynamicConfig(this,conf.underlying)

  def getConf:play.Configuration  = new play.Configuration(dynamicConfig)

  override def put(key: String, v: ConfValue): Unit = provider.put(key,v)

  override def remove(key: String): Unit = provider.remove(key)

  override def get(key: String) = provider.get(key)

  override def list = provider.list

  override def checkExists(key: String) = provider.checkExists(key)
}