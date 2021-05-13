package play.api.assets
import java.io.{File, FileInputStream, InputStream}
import java.nio.charset.Charset
import java.util.{Date, UUID}

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.util.ByteString
import com.google.common.io.Files
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

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

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

  override def link(file: File): String =  save(file.getName,new FileInputStream(file))

  def move(from:String,to:String):Future[Unit] = {
    val (b1,o1) = bucketAndObj(from)
    val (b2,o2) = bucketAndObj(to)
    s3.s3.bucket(b1).obj(o1).moveTo(s3.s3.bucket(b2).obj(o2))
  }

  def copy(from:String,to:String):Future[Unit] = {
    val (b1,o1) = bucketAndObj(from)
    val (b2,o2) = bucketAndObj(to)
    s3.s3.bucket(b1).obj(o1).copyTo(s3.s3.bucket(b2).obj(o2))
  }

  override def save(in: InputStream): String =  save("",in)

  override def asyncSave(fileName :String,in :InputStream):Future[String] = {
    implicit val w = DefaultBodyWritables.writeableOf_Bytes
    val source = StreamConverters.fromInputStream(() => in)
    val file = getFileName("",fileName)
    val uploaded: Future[NotUsed] = source.runWith(s3.s3.bucket(s3.bucket).obj(file).put[ByteString])
    uploaded.map(_ => s"${s3.bucket}/${file}")
  }

  override def getSink(fileName:String,t:Boolean):(String,Sink[ByteString, Future[NotUsed]]) = getSink(fileName)

  override def getSink(fileName:String):(String,Sink[ByteString, Future[NotUsed]]) = {
    implicit val w = DefaultBodyWritables.writeableOf_Bytes
    val file = getFileName("",fileName)
    val sink = s3.s3.bucket(s3.bucket).obj(file).put[ByteString]
    (s"${s3.bucket}/${file}") -> sink
  }

  override def save(fileName: String, in: InputStream): String =  {
    Await.result(asyncSave(fileName,in),Duration.Inf)
  }

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

  def asyncDelete(file:String):Future[Boolean] = {
    val (bucket,obj) = bucketAndObj(file)
    s3.s3.bucket(bucket).obj(obj).delete().map(_ => true)
  }

  override def delete(file: String): Boolean = {
    Await.result(asyncDelete(file),Duration.Inf)
  }

  override def getSource(file: String): Source[ByteString, NotUsed] = {
    val (bucket,obj) = bucketAndObj(file)
    s3.s3.bucket(bucket).obj(obj).get()
  }

  def bucketAndObj(file:String):(String,String) = {
    val path = UriEncoding.decodePath(file,Charset.defaultCharset()).split("/")
    if(path.size == 1) s3.bucket -> file else path.head -> path.drop(1).mkString("/")
  }

  override def at(file: String): Action[AnyContent] = builder {
    val (bucket,obj) = bucketAndObj(file)
    val data:Source[ByteString, NotUsed] = s3.s3.bucket(bucket).obj(obj).get()
    val entity = HttpEntity.Chunked(
      data.map(r => HttpChunk.Chunk(r)),
      fileMimeTypes.forFileName(file).orElse(Some(ContentTypes.BINARY))
    )
    Results.Ok.sendEntity(entity)
  }

  override def getURI(path: String): String = frontUri.getOrElse(s3.uri) + "/" + path

  override def getWorkdir(): File = throw new UnsupportedOperationException

  def underlying:OSS = s3.s3
}
