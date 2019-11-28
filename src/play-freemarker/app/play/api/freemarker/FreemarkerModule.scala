package play.api.freemarker
import java.io.{File, StringWriter, Writer}
import java.util.Locale
import javax.inject.{Inject, Provider, Singleton}

import freemarker.template.Configuration
import play.api
import play.api.inject.Module
import play.api.{Environment, Mode}
import play.twirl.api.{Content, Html, MimeTypes}

import scala.collection.JavaConverters._

trait Freemarker {
  def render(tpl: String): Content

  def render(tpl: String, data: Any): Content

  def render(tpl: String, data: Any,loc: Locale): Content

  def addShare(key:String,data:Any):Unit
}

@Singleton
class FreemarkerTemplate @Inject() (cfg:Configuration,env:Environment) extends Freemarker{

  def getWriter:Writer = new StringWriter()

  /**
    * can be cache
    * @param tp
    * @param data
    * @param loc
    * @return
    */
  override def render(tp: String, data: Any,loc: Locale): Content = {
    var tpl = tp
    val idx = tpl.lastIndexOf('.')
    if(idx < 0){
      tpl = tpl + ".html"
    }
    val cl = Thread.currentThread().getContextClassLoader
    try{
      val out = getWriter
      Thread.currentThread().setContextClassLoader(env.classLoader)
      cfg.getTemplate(tpl,loc).process(data,out)
      out.flush()
      FreemarkerContent(out.toString)
    }finally{
      Thread.currentThread().setContextClassLoader(cl)
    }
  }

  override def render(tpl: String, data: Any) = render(tpl,data,null)

  override def render(tpl: String) = render(tpl,null,null)

  override def addShare(key: String, data: Any): Unit = cfg.setSharedVariable(key,data)
}

case class FreemarkerContent(bd:String) extends Content {
  override def body = bd
  override def contentType = MimeTypes.HTML
}

@Singleton
class FreemarkerProvider @Inject() (conf:play.api.Configuration,env:Environment) extends Provider[Configuration] {

  val cfg = new Configuration(Configuration.VERSION_2_3_26)

  val TEMPLATE_PATHS_KEY = "template_paths"

  override def get(): Configuration = {
    val config = conf.getOptional[play.api.Configuration]("freemarker.config").getOrElse(play.api.Configuration.empty)
    val path = conf.getOptional[String](TEMPLATE_PATHS_KEY).getOrElse("public/views")
    val keys = config.underlying.entrySet()

    keys.asScala.foreach{entry =>
      entry.getKey match {
        case TEMPLATE_PATHS_KEY =>
        case _ =>
          cfg.setSetting(entry.getKey,String.valueOf(entry.getValue.unwrapped()))
      }
    }
    if("^\\/|^([A-Za-z0-9]:)".r.findFirstIn(path).isDefined) {
      cfg.setDirectoryForTemplateLoading(new File(path))
    }else {
      cfg.setClassLoaderForTemplateLoading(env.classLoader,path)
    }

    env.mode match {
      case Mode.Dev => cfg.setTemplateUpdateDelayMilliseconds(1)
      case _ =>
    }
    cfg
  }
}

class FreemarkerModule extends Module {
  override def bindings(environment: Environment, configuration: api.Configuration) = Seq(
    bind[Configuration].toProvider[FreemarkerProvider],
    bind[Freemarker].to[FreemarkerTemplate]
  )
}