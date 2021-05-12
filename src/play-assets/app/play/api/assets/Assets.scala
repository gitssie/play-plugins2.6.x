package play.api.assets

import java.io.FileInputStream
import java.net.URLEncoder

import javax.inject.{Inject, Singleton}
import com.google.common.io.Files
import play.api.libs.json.Json
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Assets @Inject() (store:AssetsStore,implicit val ec:ExecutionContext) extends InjectedController{
  /**
    * Generates an `Action` that serves a static resource, using the base asset path from configuration.
    */
  def at(file: String): Action[AnyContent] = Action.async{req =>
    val nameOpt = req.getQueryString("name")
    if(nameOpt.isDefined){
      val suffix = Files.getFileExtension(file)
      val name = "attachment; filename=" +  URLEncoder.encode(nameOpt.get,"UTF-8") + "." + suffix
      store.at(file)(req).map(_.withHeaders("Content-Disposition" -> name))
    }else{
      store.at(file)(req)
    }
  }

  def upload = Action.async(parse.multipartFormData) { request =>
    request.body.files.headOption.map { file =>
      store.asyncSave(file.filename,new FileInputStream(file.ref.toFile)).map(path => {
        val onode = play.libs.Json.newObject()
        val data =  play.libs.Json.newObject()
        data.put("key",path)
        data.put("realPath",path)
        data.put("url",store.getURI(path))
        onode.put("status",200)
        onode.set("data",data)
        Ok(Json.toJson(onode))
      })
    }.getOrElse {
      val onode = play.libs.Json.newObject()
      onode.put("status",400)
      onode.put("message","Missing file")
      Future.successful(Ok(Json.toJson(onode)))
    }
  }
}
