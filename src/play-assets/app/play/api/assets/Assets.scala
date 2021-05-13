package play.api.assets

import java.io.FileInputStream
import java.net.URLEncoder
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import com.google.common.io.Files
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import play.core.parsers.Multipart.FileInfo

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class Assets @Inject() (store:AssetsStore,
                        implicit val ec:ExecutionContext) extends InjectedController{

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

  def parserToSource: BodyParser[MultipartFormData[String]] = {
    val part = new AtomicBoolean()
    parse.multipartFormData({
      case FileInfo(partName, filename, contentType) =>
        if(!part.compareAndSet(false,true)){//ignore
          Accumulator.done(FilePart(partName, filename, contentType,"file part length gt "+part.get()))
        }else {
          val (path, sink) = store.getSink(filename)
          Accumulator(sink).map { _ =>
            FilePart(partName, filename, contentType, path)
          }
        }
    })
  }

  def fastUpload = Action(parserToSource){request =>
    request.body.files.headOption.map(f => uploadSuccess(f.ref)).getOrElse(uploadFailure())
  }

  def uploadFailure():Result = {
    val onode = play.libs.Json.newObject()
    onode.put("status",400)
    onode.put("message","Missing file")
    Ok(Json.toJson(onode))
  }

  def uploadSuccess(path:String):Result = {
    val onode = play.libs.Json.newObject()
    val data =  play.libs.Json.newObject()
    data.put("key",path)
    data.put("realPath",path)
    data.put("url",store.getURI(path))
    onode.put("status",200)
    onode.set("data",data)
    Ok(Json.toJson(onode))
  }

  def upload = Action.async(parse.multipartFormData) { request =>
    request.body.files.headOption.map {file =>
      store.asyncSave(file.filename,new FileInputStream(file.ref.toFile)).map(uploadSuccess(_))
    }.getOrElse {
      Future.successful(uploadFailure())
    }
  }
}
