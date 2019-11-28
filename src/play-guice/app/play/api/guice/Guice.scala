package play.api.guice

import java.net.URL
import java.util
import java.util.concurrent.Callable
import javax.inject.{Inject, Singleton}

import com.google.common.cache.CacheBuilder
import com.google.inject.matcher.Matchers
import com.google.inject.{AbstractModule, Injector}
import org.reflections.Reflections
import org.reflections.scanners.{SubTypesScanner, TypeAnnotationsScanner}
import org.reflections.util.{ClasspathHelper, ConfigurationBuilder}
import play.api.inject.{ApplicationLifecycle, Module}
import play.api.{Configuration, Environment}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Guice @Inject()(conf:Configuration,injector:Injector,applicationLifecycle: ApplicationLifecycle,environment: Environment)(implicit ec: ExecutionContext) {
  private val beansOfTypeCache = CacheBuilder.newBuilder().maximumSize(1 << 10).build[Class[_],util.List[_]]

  private lazy val reflections:Option[Reflections] = {
    val includes = conf.getOptional[Seq[String]]("play.inject.include").getOrElse(Seq.empty)
    if(includes.nonEmpty) {
      val buf = ListBuffer.empty[URL]
      includes.foreach(buf ++= ClasspathHelper.forPackage(_).asScala)

      val ref = new Reflections(new ConfigurationBuilder()
                  .setUrls(buf :_*)
                  .addClassLoader(environment.classLoader)
                  .setScanners(new SubTypesScanner(),new TypeAnnotationsScanner()))

      Some(ref)
    }else{
      None
    }
  }

  def onStart():Unit = {
    reflections.foreach(ref => ref.getTypesAnnotatedWith(classOf[Singleton]).asScala.foreach(injector.getInstance(_)))
  }

  def getBeansOfType[T](clazz: Class[T]): util.List[T] = beansOfTypeCache.get(clazz,new Callable[util.List[_]] {
    override def call(): util.List[_] = {
      reflections.map(_.getSubTypesOf(clazz).asScala
        .filter(_.isAnnotationPresent(classOf[Singleton]))
        .map(injector.getInstance(_)))
        .getOrElse(Seq.empty).toList.asJava
    }
  }).asInstanceOf[util.List[T]]

  onStart
}

class GuiceModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[Guice].toSelf.eagerly()
  )
}
class GuiceInterceptorModule extends AbstractModule {
  override def configure(): Unit = {
    bindInterceptor(Matchers.any, Matchers.annotatedWith(classOf[With]), new AspectInterceptor)
  }
}