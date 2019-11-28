package play.api.httpproxy

import java.util
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.{Callable, TimeUnit}
import javax.inject.{Inject, Singleton}

import com.google.common.cache.CacheBuilder
import com.google.common.util.concurrent.RateLimiter
import com.typesafe.config.Config
import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.channel.Channel
import org.jboss.netty.handler.codec.http._
import org.littleshoot.proxy.{DefaultHttpProxyServer, HttpProxyServer, ProxyCacheManager}
import org.slf4j.LoggerFactory
import play.api
import play.api.inject.{ApplicationLifecycle, Module}
import play.api.{Configuration, Environment}

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.{Failure, Try}

@Singleton
class HttpProxyPlugin @Inject() (conf:Configuration,applicationLifecycle: ApplicationLifecycle){
  private val logger = LoggerFactory.getLogger(classOf[HttpProxyServer])
  private val serverInstance = new AtomicReference[HttpProxyServer]()

  def onStart:Unit = {
    val port = conf.getOptional[Int]("play.http.proxyServer.port").getOrElse(8080)
    val bind = conf.getOptional[String]("play.http.proxyServer.bind").getOrElse("0.0.0.0")
    val server = createProxyServer(bind,port)
    server.start()
    serverInstance.set(server)
  }

  def onClose:Unit = {
    Option(serverInstance.get()).foreach(_.stop())
  }

  lazy val filterGuard:List[PartialFunction[HttpRequest,HttpResponse]] = {
    val maxwait = conf.getOptional[Duration]("play.http.proxyServer.max-wait").getOrElse(Duration.apply("15s"))
    val list = conf.underlying.getConfigList("play.http.proxyServer.filter");
    list.asScala.map(c => {
      val hosts = c.getStringList("host").asScala
      new PartialFunction[HttpRequest,HttpResponse](){
        override def isDefinedAt(req: HttpRequest):Boolean = {
          val host = getHost(req)
          hosts.exists(host == _)
        }
        override def apply(req: HttpRequest):HttpResponse = {
          val (key,rl) = getRateLimiter(req,c)
          val success = rl.tryAcquire(1,maxwait.toSeconds,TimeUnit.SECONDS)
          if(success) {
            val host = getHost(req)
            if (logger.isInfoEnabled) {
              logger.info("rate limit for key:" + key + ",rate:" + rl.getRate + "/s,inst:" + rl.hashCode() + ",host:" + host)
            }
            null
          }else{
            throw new IllegalStateException("request wait timeout:"+key)
          }
        }
      }
    }).toList
  }

  private def getRate(key:String,c:Config):Double = {
      val conf = Configuration(c)
      val rateStr = conf.getOptional[String]("rate-strategy."+key).getOrElse(conf.get[String]("rate"))
      rateStr.replace("/s","").toDouble
  }


  private val userAgentRg = """\(Wechat/([\w\d\W_-]+)\)""".r
  private val rateLimiterCache = CacheBuilder.newBuilder().maximumSize(1 << 10).build[String,RateLimiter]()

  /**
    * User-Agent 的格式 (Wechat/tag)
    * tag为 服务商商号的MD5值
    * @param req
    * @return
    */
  def getRateLimiter(req:HttpRequest,conf:Config):(String,RateLimiter) = {
    val userAgent = req.getHeader("User-Agent");
    userAgentRg.findFirstMatchIn(userAgent).filter(_.groupCount > 0).map{f =>
      val key = f.group(1)
      val rl = getRateLimiter(key,conf)
      (key -> rl)
    }.getOrElse(("global" -> getRateLimiter("global",conf)))
  }

  def getRateLimiter(key:String,conf:Config): RateLimiter ={
    var rateKey = key
    val idx = rateKey.indexOf("-")
    if(idx > 0){
      rateKey = rateKey.substring(0,idx)
    }
    val rate = getRate(rateKey,conf)
    rateLimiterCache.get(key,new Callable[RateLimiter] {
      override def call(): RateLimiter = {
        RateLimiter.create(rate)
      }
    })
  }

  def getHost(req: HttpRequest): String ={
    var host = req.getHeader("Host")
    val idx = host.indexOf(":")
    if(idx > 0) host = host.substring(0,idx)
    host
  }

  def createProxyServer(bind:String,port: Int):HttpProxyServer = {
    val server = new DefaultHttpProxyServer(port,new ProxyCacheManager {
      override def cache(originalRequest: HttpRequest, httpResponse: HttpResponse, response: scala.Any, encoded: ChannelBuffer): util.concurrent.Future[String] = null

      override def returnCacheHit(request: HttpRequest, channel: Channel): Boolean = {
        Try{
          filterGuard.find(_.isDefinedAt(request)).map(_.apply(request))
        } match {
          case Failure(t) => {
            channel.close()
            logger.error("filter guard error",t)
          }
          case _ =>
        }
        false
      }
    })

    logger.info("Http Proxy Server Listening on {}", port)

    server
  }

  onStart

  applicationLifecycle.addStopHook(() => Future.successful(onClose))
}

class HttpProxyModule extends Module {
  override def bindings(environment: Environment, configuration: api.Configuration) = Seq(
    bind[HttpProxyPlugin].toSelf.eagerly()
  )
}
