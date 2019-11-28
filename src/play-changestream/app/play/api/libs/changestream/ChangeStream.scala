package play.api.libs.changestream

import java.io.{FileOutputStream, IOException}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import akka.util.Timeout
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.shyiko.mysql.binlog.BinaryLogClient
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import play.api.db.Database
import play.api.{Configuration, Environment}
import play.libs.Json

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.io.Source

@Singleton
class ChangeStream @Inject()(cf: Config, env:Environment,val system:ActorSystem, val ds: Database,implicit val ec: ExecutionContext){
  protected val log = LoggerFactory.getLogger(getClass)

  protected val config = cf.getConfig("play.changestream")
  protected val mysqlHost = config.getString("mysql.host")
  protected val mysqlPort = config.getInt("mysql.port")

  val conf = Configuration(config)

  protected lazy val client = new BinaryLogClient(
    mysqlHost,
    mysqlPort,
    config.getString("mysql.user"),
    config.getString("mysql.password")
  )

  @volatile
  protected var isPaused = false

  protected implicit val timeout = Timeout(10 seconds)

  /** Every changestream instance must have a unique server-id.
    *
    * http://dev.mysql.com/doc/refman/5.7/en/replication-setup-slaves.html#replication-howto-slavebaseconfig
    */
  client.setServerId(config.getLong("mysql.server-id"))

  /** If we lose the connection to the server retry every `changestream.mysql.keepalive` milliseconds. **/
  client.setKeepAliveInterval(config.getLong("mysql.keepalive"))

  /** Register the objects that will receive `onEvent` calls and deserialize data **/
  client.registerEventListener(new ChangeStreamEventListener(config,ds,this,env))
  client.setEventDeserializer(ChangestreamEventDeserializer)

  /** Register the object that will receive BinaryLogClient connection lifecycle events **/
  client.registerLifecycleListener(ChangeStreamLifecycleListener)

  def serverName = s"${mysqlHost}:${mysqlPort}"
  def clientId = client.getServerId

  def currentPosition = {
    s"${client.getBinlogFilename}:${client.getBinlogPosition}"
  }

  def isConnected = client.isConnected

  def connect() = {
    if(!client.isConnected()) {
      isPaused = false
      system.scheduler.scheduleOnce(Duration.create(10,TimeUnit.SECONDS))(getConnected)
      true
    }
    else {
      false
    }
  }

  def disconnect() = {
    log.info("Shutting down...")
    if(client.isConnected()) {
      isPaused = true
      client.disconnect()
      true
    }
    else {
      false
    }
  }

  def saveBinlogPosition = {
    val sf =  env.getFile("slave.info")
    val onode = Json.newObject()
    onode.put("filename",client.getBinlogFilename)
    onode.put("position",client.getBinlogPosition)
    onode.put("gtidset",client.getGtidSet)
    val out = new FileOutputStream(sf)
    out.write(onode.toString.getBytes)
    out.close()
  }

  def loadBinlogPosition = {
    env.getExistingFile("slave.info").map(f => {
      val in = Source.fromFile(f,"UTF-8").mkString
      val onode = Json.parse(in).asInstanceOf[ObjectNode]
      (onode.get("filename").asText("") -> onode.get("position").asLong(0))
    })
  }

  protected def getConnected = {
    /** Finally, signal the BinaryLogClient to start processing events **/
    log.info(s"Starting changestream...")
    while(!isPaused && !client.isConnected) {
      try {
        loadBinlogPosition.map(f => {
          client.setBinlogFilename(f._1)
          client.setBinlogPosition(f._2)
        })
        client.connect()
      }
      catch {
        case e: IOException =>
          log.error("Failed to connect to MySQL to stream the binlog, retrying...", e)
          Thread.sleep(5000)
        case e: Exception =>
          log.error("Failed to connect to MySQL.", e)
          Thread.sleep(10000)
      }
    }
  }
}