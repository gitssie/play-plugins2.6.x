package play.api.libs.changestream.actors

import java.util.concurrent.CompletionStage

import com.typesafe.config.Config
import play.libs.ws.{WSClient, WSResponse}

class EsProxy(conf:Config,ws: WSClient) {
  val esUrl = conf.getString("play.changestream.es.host")

  def bulkRow(data:String): CompletionStage[WSResponse] ={
    val url = esUrl + "/_bulk"
    ws.url(url).addHeader("Content-Type","application/x-ndjson").post(data)
  }
}
