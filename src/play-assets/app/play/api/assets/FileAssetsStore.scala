package play.api.assets

import java.io._
import java.net.URL
import java.nio.file.{Files => JFiles}
import java.util.{Date, UUID}

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink, Source, StreamConverters}
import akka.util.ByteString
import com.google.common.io._
import controllers.{AssetsConfiguration, DefaultAssetsMetadata, Assets => PAssets}
import javax.inject.{Inject, Singleton}
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.FastDateFormat
import play.api.http.{FileMimeTypes, HttpErrorHandler}
import play.api.libs.crypto.CookieSigner
import play.api.{Configuration, Environment}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class FileAssetsStore @Inject() (conf:Configuration,
                                 env:Environment,
                                 singer:CookieSigner,
                                 errorHandler: HttpErrorHandler,
                                 config: AssetsConfiguration,
                                 implicit val ec: ExecutionContext,
                                 implicit val mat: Materializer,
                                 fileMimeTypes: FileMimeTypes) extends AssetsStore {

  val baseDirPath = conf.getOptional[String]("play.assets.store.file.path").getOrElse("assets")
  val workdirPath = conf.getOptional[String]("play.workdir.store.file.path").getOrElse("workdir")
  val tmpDirPath = "tmp"
  val df = FastDateFormat.getInstance("yyyyMMdd")
  val baseDir =  baseDirPath.startsWith("/") match {
    case true => new File(baseDirPath)
    case false =>  env.getFile(baseDirPath)
  }
  val assets = new PAssets(errorHandler,new DefaultAssetsMetadata(config,getResource _,fileMimeTypes))

  def getResource(path: String): Option[URL] = {
    val resourceFile = new File(path)
    val pathFile = baseDir
    if (!resourceFile.getCanonicalPath.startsWith(pathFile.getCanonicalPath)) {
      None
    } else if(resourceFile.isFile && resourceFile.exists()){
        Some(resourceFile.toURI.toURL)
    }else{
      None
    }
  }

  def getFileName(prefix:String,fileName:String):String = {
    var randomName = df.format(new Date()) + "/" + singer.sign(UUID.randomUUID().toString)
    if(StringUtils.isNoneBlank(prefix)){
      randomName = prefix + "/" + randomName
    }
    if(StringUtils.isNoneBlank(fileName)){
      val idx = fileName.lastIndexOf(".")
      if(idx >= 0){
        randomName = randomName + fileName.substring(idx,fileName.length)
      }
    }
    randomName
  }

  def getFile(fileName:String):File = new File(baseDir,fileName)

  override def save(in: InputStream):String = save("",in)

  override def save(name: String, in: InputStream):String = {
    Await.result(asyncSave(name,in),Duration.Inf)
  }

  override def asyncSave(name :String,in :InputStream):Future[String] = {
    val fileName = getFileName("",name)
    val file = getFile(fileName)
    Files.createParentDirs(file)
    val source = StreamConverters.fromInputStream(() => in)
    source.to(FileIO.toPath(file.toPath)).run().transform({
      case Success(r) => {
        if(r.status.isFailure){
          if(file.exists()) file.delete()
        }
        Try(fileName)
      }
      case Failure(e) => {
        if(file.exists()) file.delete()
        Try(throw e)
      }
    })
  }


  override def delete(path: String):Boolean = {
    val resourceFile = getFile(path)
    val pathFile = baseDir
    if (!resourceFile.getCanonicalPath.startsWith(pathFile.getCanonicalPath)) {
      false
    } else if(resourceFile.isFile && resourceFile.exists()){
      resourceFile.delete()
    }else{
      false
    }
  }

  override def getSource(path: String):Source[ByteString, NotUsed] = {
    val resourceFile = getFile(path)
    val pathFile = baseDir
    if (!resourceFile.getCanonicalPath.startsWith(pathFile.getCanonicalPath)) {
      Source.failed(throw new FileNotFoundException)
    } else if(resourceFile.isFile && resourceFile.exists()){
      FileIO.fromPath(resourceFile.toPath).mapMaterializedValue(_ => NotUsed)
    }else{
      Source.failed(throw new FileNotFoundException)
    }
  }

  override def at(path: String) = assets.at(baseDir.getPath,path)

  def getFile(name: String,isTmp: Boolean): (String,File) ={
    val fileName = isTmp match {
      case true => getFileName(tmpDirPath,name)
      case false => getFileName("",name)
    }
    val file = getFile(fileName)
    Files.createParentDirs(file)
    (fileName -> file)
  }

  override def link(path: File):String = {
    val (fileName,file) = getFile(path.getName,true)
    try{
      JFiles.createSymbolicLink(file.toPath,path.toPath)
    }catch{
      case e:UnsupportedOperationException => {
        Files.copy(path,file)
      }
    }
    fileName
  }

  override def getSink(name: String):(String,Sink[ByteString, Future[NotUsed]]) = getSink(name,false)

  override def getSink(name:String,isTmp: Boolean):(String,Sink[ByteString, Future[NotUsed]]) = {
    val (fileName, file) = getFile(name, isTmp)
    fileName -> FileIO.toPath(file.toPath).mapMaterializedValue(_.transform{
      case Success(r) => {
        if(r.status.isFailure){
          if(file.exists()) file.delete()
        }
        Try(NotUsed)
      }
      case Failure(e) => {
        if(file.exists()) file.delete()
        Try(throw e)
      }
    })
  }

  override def getURI(path: String): String = path

  override def getWorkdir():File = {
    val file = env.getFile(workdirPath)
    if(!file.exists()){
      JFiles.createDirectories(file.toPath)
    }
    file
  }
}
