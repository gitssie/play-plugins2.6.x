package play.api.libs.changestream.actors

import java.util
import java.util.concurrent.ThreadLocalRandom
import java.util.{Date, TimeZone}

import akka.actor.{ActorSystem, Cancellable}
import com.fasterxml.jackson.databind.JsonNode
import com.google.common.collect.Maps
import com.typesafe.config.Config
import org.apache.commons.lang3.time.{DateUtils, FastDateFormat}
import org.slf4j.LoggerFactory
import play.api.libs.changestream.events.MutationWithInfo
import play.api.libs.crypto.CookieSigner
import play.api.{Configuration, Play}
import play.libs.Json
import play.libs.ws.WSClient

import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer
import scala.compat.java8.FutureConverters
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext}
import scala.util.{Failure, Success, Try}

class EsActor(conf:Config,sa: StateActor) extends SyncActor{
  protected val log = LoggerFactory.getLogger(getClass)
  lazy val ws:WSClient = Play.privateMaybeApplication.get.injector.instanceOf[WSClient]
  lazy val system:ActorSystem = Play.privateMaybeApplication.get.injector.instanceOf[ActorSystem]
  lazy val cookieSigner = Play.privateMaybeApplication.get.injector.instanceOf[CookieSigner]
  implicit lazy val ec = Play.privateMaybeApplication.get.injector.instanceOf[ExecutionContext]

  var tickRun:Cancellable = null
  var lastFlush = System.currentTimeMillis()
  val cf = Configuration(conf)
  val timeout = cf.getOptional[Duration]("es.batch-timeout").getOrElse(Duration.apply("1m"))
  val tickeTime = cf.getOptional[Duration]("es.batch-tick").getOrElse(Duration.apply("10s"))
  val batchSize = cf.getOptional[Int]("es.batch-size").getOrElse(100)
  val timestamp = cf.getOptional[Configuration]("es.timestamp").getOrElse(Configuration.empty)
  val timestampField = cf.getOptional[String]("es.timestamp.field").getOrElse("@timestamp")
  val esUrl = conf.getString("es.host")
  val timeZone = TimeZone.getTimeZone(cf.getOptional[String]("es.time-zone").getOrElse("UTC"))
  val df = FastDateFormat.getInstance(cf.getOptional[String]("es.time-format").getOrElse("yyyy-MM-dd'T'HH:mm:ss+0800"),timeZone)
  val idxPrefix = cf.getOptional[String]("es.index-prefix").getOrElse("")
  val needSign = cf.getOptional[String]("es.secret").isDefined
  val idxDatePattern = FastDateFormat.getInstance(cf.getOptional[String]("es.index-pattern").getOrElse("yyyy.MM"),timeZone)
  val batchList = ListBuffer.empty[MutationWithInfo]

  def receive = {
    case m@MutationWithInfo(_, _, _, Some(message: AnyRef)) =>
      batchList.synchronized{
        batchList += m
      }
      if(batchList.size >= batchSize){
        flush()
      }
      ticked()
  }

  def flush():Unit = {
    if(batchList.isEmpty) return
    val rowCount = batchList.size
    val cur = System.currentTimeMillis()
    if(cur - lastFlush >= tickeTime.toMillis || rowCount >= batchSize) {
      val buf = ListBuffer.empty[JsonNode]
      batchList.synchronized {
        batchList.foreach { m =>
          val data = m.message.get.asInstanceOf[ListMap[String, Any]]
          val meta = getIndexName(data, m)
          val node = Json.newObject()
          val onode = Json.newObject()
          onode.put("_index", meta._1);
          onode.put("_type", meta._2);
          onode.put("_id", meta._3);
          node.set("index", onode)
          buf += node
          buf += Json.toJson(toMapRow(data,m.mutation.tableName))
        }
        batchList.clear()
      }
      bulkRow(buf)
      lastFlush = System.currentTimeMillis()
      log.info("success flush data rows:"+rowCount+",took:"+ (lastFlush - cur))
    }
  }

  def ticked(): Unit = {
    if(tickRun == null) {
      val time = tickeTime.asInstanceOf[FiniteDuration]
      tickRun = system.scheduler.schedule(time, time, () => flush())
    }
  }

  def bulkRow(rows:ListBuffer[JsonNode]): Unit ={
    val url = esUrl + "/_bulk"
    val data = rows.mkString("","\n","\n")
    var p = ws.url(url)
    if(needSign){
      p = p.addHeader("X-HMAC-SIGN",cookieSigner.sign(data))
    }
    p = p.addHeader("Content-Type","application/x-ndjson")
    val r = p.post(data)
    val f = FutureConverters.toScala(r).map(r => {
      if(r.getStatus == 200){
        sa.receive(Success(r.getStatus))
      }else{
        log.error("bulk failure:"+r.getBody)
        sa.receive(Failure(new IllegalStateException(r.getStatusText)))
      }
    })
    Try(Await.result(f,timeout)) match {
      case Failure(t) => sa.receive(Failure(t))
      case Success(_) =>
    }
  }

  def putRow(row:ListMap[String,Any],message:MutationWithInfo): Unit ={
    val idxName = getIndexName(row,message)
    val url = esUrl + idxName
    val r = ws.url(url).post(Json.toJson(toMapRow(row,message.mutation.tableName)))
    val f = FutureConverters.toScala(r).map(r => {
      if(r.getStatus == 200){
          sa.receive(Success(r.getStatus))
      }else{
        sa.receive(Failure(new IllegalStateException(r.getStatusText)))
      }
    })
    Try(Await.result(f,timeout)) match {
      case Failure(t) => sa.receive(Failure(t))
      case Success(_) =>
    }
  }

  def toMapRow(row:ListMap[String,Any],tableName:String):util.Map[String,Any] = {
    val data = Maps.newHashMapWithExpectedSize[String,Any](row.size * 2)
    var time:Date = new Date()
    row.map{
      case (k,v) => {
        v match {
          case d:Date => {
            data.put(k,df.format(d))
            if(!time.after(d)){
              time = d
            }
          }
          case x => data.put(k,x)
        }
      }
    }
    val opt = timestamp.getOptional[String](tableName)
    if(opt.isDefined){
      var d = data.get(opt.get)
      if(d == null){
        d = df.format(time)
      }
      data.put(timestampField,d)
    }else{
      data.put(timestampField,df.format(time))
    }
    data
  }

  def getIndexType(message:MutationWithInfo) = message.mutation.tableName

  def getIndexName(row:ListMap[String,Any],message:MutationWithInfo) = {
    val primaryKeys = message.columns.get.columns.collect({ case col if col.isPrimary => col })
    var idxName = idxPrefix + message.mutation.database
    var id:String = ""
    if(!primaryKeys.isEmpty){
      val idCol = row.get(primaryKeys.head.name)
      if(primaryKeys.size > 1 && row.get(primaryKeys(1).name).isDefined){
        row.get(primaryKeys(1).name).get match {
          case d:Date => {
            idxName = idxName + "-" + idxDatePattern.format(d)
          }
          case _ =>
        }
      }
      id = idCol.getOrElse(ThreadLocalRandom.current().nextLong()).toString
    }
    (idxName,message.mutation.tableName,id)
  }
}