package play.api.libs.changestream.actors

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import play.api.libs.changestream.events._
import play.libs.Json

import scala.collection.immutable.ListMap

class MapFormatterActor(nextHop:SyncActor) extends SyncActor {
  protected val log = LoggerFactory.getLogger(getClass)

  def receive = {
    case message: MutationWithInfo if message.columns.isDefined => {
      //log.debug(s"Received ${message.mutation} for table ${message.mutation.database}.${message.mutation.tableName}")

      //val primaryKeys = message.columns.get.columns.collect({ case col if col.isPrimary => col.name })
      val rowData = getRowData(message)
      //val oldRowData = getOldRowData(message)

      rowData.indices.foreach({ idx =>
        val row = rowData(idx)

        /**
          * val oldRow = oldRowData.map(_(idx))
          * val pkInfo = ListMap(primaryKeys.map({
          * case k:String => k -> row.getOrElse(k, NullNode.getInstance())
          * }):_*)
          * val payload =
          * getJsonHeader(message, pkInfo, row, idx, rowData.length) ++
          * transactionInfo(message, idx, rowData.length) ++
          * getJsonRowData(row) ++
          * updateInfo(oldRow)
          * val json = Json.toJson(payload.asJava)
          **/
        nextHop.receive(message.copy(message = Some(row)))
      })
    }
    case _ =>
  }

  protected def getJsonString(v: JsonNode):String = v.toString

  protected def getRowData(message: MutationWithInfo) = {
    val columns = message.columns.get.columns
    val mutation = message.mutation

    mutation.rows.map(row =>
      ListMap(columns.indices.map({
        case idx if mutation.includedColumns.get(idx) =>
          columns(idx).name -> row(idx)
      }).collect({
        case (k:String, v:Any) => k -> v
      }):_*)
    )
  }

  protected def getOldRowData(message: MutationWithInfo) = {
    val columns = message.columns.get.columns
    val mutation = message.mutation

    mutation match {
      case update:Update =>
        Some(update.oldRows.map(row =>
          ListMap(columns.indices.map({
            case idx if mutation.includedColumns.get(idx) =>
              columns(idx).name -> row(idx)
          }).collect({
            case (k:String, v:Any) => k -> v
          }):_*)
        ))
      case _ => None
    }
  }

  protected def transactionInfo(message: MutationWithInfo, rowOffset: Long, rowsTotal: Long): ListMap[String, JsonNode] = {
    message.transaction match {
      case Some(txn) => ListMap(
        "transaction" -> Json.newObject().put(
          "id" , txn.gtid).put(
          "current_row" ,txn.currentRow + rowOffset).put(
          "last_mutation", if((rowOffset == rowsTotal - 1) && txn.lastMutationInTransaction) true else false))
      case None => ListMap.empty
    }
  }

  protected def getJsonHeader(
                               message: MutationWithInfo,
                               pkInfo: ListMap[String, JsonNode],
                               rowData: ListMap[String, JsonNode],
                               currentRow: Long,
                               rowsTotal: Long
                             ): ListMap[String, JsonNode] = {
    ListMap(
      "mutation" -> Json.toJson(message.mutation.toString),
      "sequence" -> Json.toJson(message.mutation.sequence + currentRow),
      "database" -> Json.toJson(message.mutation.database),
      "table" -> Json.toJson(message.mutation.tableName),
      "query" -> Json.newObject().put(
        "timestamp",message.mutation.timestamp).put(
        "sql",message.mutation.sql.getOrElse("")).put(
        "row_count" , rowsTotal).put(
        "current_row", currentRow + 1)
      ,
      "primary_key" -> Json.toJson(pkInfo)
    )
  }
}
