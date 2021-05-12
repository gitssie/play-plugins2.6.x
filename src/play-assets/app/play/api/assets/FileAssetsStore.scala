package play.api.assets

import java.io._
import java.net.URL
import java.util.{Date, UUID}

import javax.inject.{Inject, Singleton}
import java.nio.file.{Files => JFiles}

import com.google.common.io._
import controllers.{AssetsConfiguration, DefaultAssetsMetadata, Assets => PAssets}
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.FastDateFormat
import play.api.http.{FileMimeTypes, HttpErrorHandler}
import play.api.libs.crypto.CookieSigner
import play.api.{Configuration, Environment}

import scala.concurrent.Future

@Singleton
class FileAssetsStore @Inject() (conf:Configuration,
                                 env:Environment,
                                 singer:CookieSigner,
                                 errorHandler: HttpErrorHandler,
                                 config: AssetsConfiguration,
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

  @throws[IOException]
  private def copy(source: InputStream, sink: OutputStream):Long = {
    var nread = 0L
    val buf = new Array[Byte](8192)
    var n = 1
    while(n > 0){
      n = source.read(buf)
      if(n > 0){
        sink.write(buf, 0, n)
      }
    }
    source.close()
    sink.close()
    nread
  }


  override def save(name: String, in: InputStream):String = {
    val fileName = getFileName("",name)
    val file = getFile(fileName)
    Files.createParentDirs(file)
    copy(in,new FileOutputStream(file))
    fileName
  }

  override def asyncSave(fileName :String,in :InputStream):Future[String] = Future.successful(save(fileName,in))


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

  override def getSource(path: String):ByteSource = {
    val resourceFile = getFile(path)
    val pathFile = baseDir
    if (!resourceFile.getCanonicalPath.startsWith(pathFile.getCanonicalPath)) {
      null
    } else if(resourceFile.isFile && resourceFile.exists()){
      Files.asByteSource(resourceFile)
    }else{
      null
    }
  }

  override def at(path: String) = assets.at(baseDir.getPath,path)

  override def getSink(name: String,isTmp: Boolean):(String,ByteSink) = {
    val (fileName,file) = getFile(name,isTmp)
    val sink = Files.asByteSink(file)
    (fileName -> sink)
  }

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
          copy(new BufferedInputStream(new FileInputStream(path)),new BufferedOutputStream(new FileOutputStream(file)))
      }
    }
    fileName
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
