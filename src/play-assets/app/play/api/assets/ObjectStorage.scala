package play.api.assets
import java.io.{File, InputStream}
import java.nio.charset.Charset
import java.util.{Date, UUID}

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Source, StreamConverters}
import akka.util.ByteString
import com.google.common.io.{ByteSink, ByteSource, Files}
import com.zengularity.benji.s3.S3
import com.zengularity.benji.{ObjectStorage => OSS}
import controllers.AssetsConfiguration
import javax.inject.{Inject, Singleton}
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.time.FastDateFormat
import play.api.Configuration
import play.api.http._
import play.api.libs.crypto.CookieSigner
import play.api.libs.ws.DefaultBodyWritables
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.mvc._
import play.utils.UriEncoding

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ObjectStorage @Inject() (conf:Configuration,
                               singer:CookieSigner,
                               builder:DefaultActionBuilder,
                               config: AssetsConfiguration,
                               implicit val ec: ExecutionContext,
                               implicit val ws: StandaloneAhcWSClient,
                               implicit val mat: Materializer,
                               fileMimeTypes: FileMimeTypes) extends AssetsStore {

  private case class S3W(s3:OSS,bucket:String,uri:String)

  private lazy val HttpUrl = "^(http[s]*)://([^:]+):([^@]+)@(.+)$".r

  private val frontUri = conf.getOptional[String]("play.assets.store.s3.front-uri")

  //S3 init
  private val s3:S3W = {
    conf.get[String]("play.assets.store.s3.uri") match {
      case HttpUrl(scheme, accessKey, secret, raw) =>
        val uri = new java.net.URL(s"${scheme}://${raw}")
        val u = if(uri.getPort <= 0) s"${scheme}://${uri.getHost}" else s"${scheme}://${uri.getHost}:${uri.getPort}"
        S3W(S3(accessKey,secret, scheme, raw)(ws),Files.getNameWithoutExtension(uri.getPath),u)
      case uri => throw new IllegalArgumentException( s"Expected URI containing accessKey and secretKey: $uri")
    }
  }

  override def link(file: File): String =  throw new UnsupportedOperationException

  override def getSink(fileName: String, isTmp: Boolean): (String, ByteSink) =  throw new UnsupportedOperationException

  override def save(in: InputStream): String =  throw new UnsupportedOperationException

  override def asyncSave(fileName :String,in :InputStream):Future[String] = {
    implicit val w = DefaultBodyWritables.writeableOf_Bytes
    val source = StreamConverters.fromInputStream(() => in)
    val file = getFileName("",fileName)
    val uploaded: Future[NotUsed] = source.runWith(s3.s3.bucket(s3.bucket).obj(file).put[ByteString])
    uploaded.map(_ => s"/${s3.bucket}/${file}")
  }

  override def save(fileName: String, in: InputStream): String =  throw new UnsupportedOperationException

  private def getFileName(prefix:String,fileName:String):String = {
    var randomName = FastDateFormat.getInstance("yyyyMMdd").format(new Date()) + "/" + singer.sign(UUID.randomUUID().toString)
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

  override def delete(path: String): Boolean =  throw new UnsupportedOperationException

  override def getSource(path: String): ByteSource =  throw new UnsupportedOperationException

  override def at(file: String): Action[AnyContent] = builder {
    val path = UriEncoding.decodePath(file,Charset.defaultCharset()).split("/")
    val (bucket,obj) = if(path.size == 1) s3.bucket -> file else path.head -> path.drop(1).mkString("/")
    val data:Source[ByteString, NotUsed] = s3.s3.bucket(bucket).obj(obj).get()
    val entity = HttpEntity.Chunked(
      data.map(r => HttpChunk.Chunk(r)),
      fileMimeTypes.forFileName(path.last).orElse(Some(ContentTypes.BINARY))
    )
    Results.Ok.sendEntity(entity)
  }

  override def getURI(path: String): String = frontUri.getOrElse(s3.uri) + path

  override def getWorkdir(): File = throw new UnsupportedOperationException

  def underlying:OSS = s3.s3
}
