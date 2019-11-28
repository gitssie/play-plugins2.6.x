package play.api.libs.changestream.actors

import java.sql.ResultSet

import org.slf4j.LoggerFactory
import play.api.db.Database
import play.api.libs.changestream.events.{MutationWithInfo, _}

import scala.collection.mutable
import scala.util.{Failure, Success, Try}

object ColumnInfoActor {
  val COLUMN_NAME = 1
  val DATA_TYPE = 2
  val IS_PRIMARY = 3
  val DATABASE_NAME = 4
  val TABLE_NAME = 5
  val PRELOAD_POSITION = 1

  case class PendingMutation(schemaSequence: Long, event: MutationWithInfo)
}

class ColumnInfoActor (nextHop:SyncActor,pool: Database) extends SyncActor {
  import ColumnInfoActor._
  protected val log = LoggerFactory.getLogger(getClass)

  // Mutable State
  protected var _schemaSequence = -1
  protected def getNextSchemaSequence: Long = {
    _schemaSequence += 1
    _schemaSequence
  }
  protected val columnsInfoCache = mutable.HashMap.empty[(String, String), ColumnsInfo]
  protected val mutationBuffer = mutable.HashMap.empty[(String, String), List[PendingMutation]]

  def receive = {
    case event: MutationWithInfo =>
      log.debug(s"Received mutation event on table ${event.mutation.cacheKey}")

      columnsInfoCache.get(event.mutation.cacheKey) match {
        case info: Some[ColumnsInfo] =>
          log.debug(s"Found column info for event on table ${event.mutation.cacheKey}")
          nextHop.receive(event.copy(columns = info))

        case None =>
          log.debug(s"Couldn't find column info for event on table ${event.mutation.cacheKey} -- buffering mutation and kicking off a query")
          val pending = PendingMutation(getNextSchemaSequence, event)
          mutationBuffer(event.mutation.cacheKey) = mutationBuffer.get(event.mutation.cacheKey).fold(List(pending))(buffer =>
            buffer :+ pending
          )

          requestColumnInfo(pending.schemaSequence, event.mutation.database, event.mutation.tableName)
      }

    case columnsInfo: ColumnsInfo =>
      log.debug(s"Received column info for event on table ${columnsInfo.cacheKey}")

      columnsInfoCache(columnsInfo.cacheKey) = columnsInfo

      mutationBuffer.remove(columnsInfo.cacheKey).foreach({ bufferedMutations =>
        // only satisfy mutations that came in after this column info was requested to avoid timing issues with several alters on the same table in quick succession
        val (ready, stillPending) = bufferedMutations.partition(mutation => columnsInfo.schemaSequence <= mutation.schemaSequence)
        mutationBuffer.put(columnsInfo.cacheKey, stillPending)

        if(ready.size > 0) {
          ready.foreach(e => nextHop.receive(e.event.copy(columns = Some(columnsInfo))))
        }
      })

    case alter: AlterTableEvent =>
      log.debug(s"Refreshing the cache due to alter table (${alter.cacheKey}): ${alter.sql}")

      columnsInfoCache.remove(alter.cacheKey)
      requestColumnInfo(getNextSchemaSequence, alter.database, alter.tableName)
  }

  protected def requestColumnInfo(schemaSequence: Long, database: String, tableName: String) = {
    Try(getColumnsInfo(schemaSequence, database, tableName)) match {
      case Failure(t) =>
        log.error(s"Couldn't fetch column info for ${database}.${tableName}", t)
        throw t
      case Success(Some(result)) => receive(result)
      case Success(None) => log.warn(s"No column metadata found for table ${database}.${tableName}")
    }
  }

  protected def getColumnsInfo(schemaSequence: Long, database: String, tableName: String): Option[ColumnsInfo] = {
    val escapedDatabase = database.replace("'", "\\'")
    val escapedTableName = tableName.replace("'", "\\'")
    val conn = pool.getConnection()
    try{
      val st = conn.createStatement();
      val sql = s"""
                   | select
                   |   col.COLUMN_NAME,
                   |   col.DATA_TYPE,
                   |   case when col.COLUMN_KEY = 'PRI' then true else false end as IS_PRIMARY
                   | from INFORMATION_SCHEMA.COLUMNS col
                   | where col.TABLE_SCHEMA = '${escapedDatabase}'
                   |   and col.TABLE_NAME = '${escapedTableName}'
                   | order by col.ORDINAL_POSITION
      """.stripMargin
      st.execute(sql)
      val rs = st.getResultSet
      val list = mutable.ListBuffer.empty[Column]
      while(rs.next()){
         list += getColumnForRow(rs)
      }
      rs.close()
      st.close()

      val res:Option[ColumnsInfo] = if(!list.isEmpty){
        Option.apply(ColumnsInfo(
            schemaSequence,
            database,
            tableName,
            list.toIndexedSeq
          ))}else {Option.empty}
      res
     }finally{
      conn.close()
    }
  }

  protected def getColumnForRow(row: ResultSet): Column = {
    Column(
      name = row.getString(COLUMN_NAME),
      dataType = row.getString(DATA_TYPE),
      isPrimary = row.getShort(IS_PRIMARY) match {
        case 0 => false
        case 1 => true
      }
    )
  }
}