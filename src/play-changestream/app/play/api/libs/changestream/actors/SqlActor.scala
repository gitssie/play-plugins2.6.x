package play.api.libs.changestream.actors

import java.util.{Date, TimeZone}

import com.typesafe.config.Config
import org.apache.commons.codec.binary.Hex
import org.apache.commons.lang3.time.FastDateFormat
import play.api.libs.changestream.events.{Delete, Insert, MutationWithInfo, Update}

import scala.collection.immutable.ListMap

class SqlActor (conf:Config,sa: StateActor) extends SyncActor{

  val buf = new StringBuilder

  override def receive = {
    case m:MutationWithInfo => m.mutation match {
      case insert:Insert => if(matchRow(m)){
        buf.clear()
        parseInsert(m,insert,buf)
        processRow(buf,sa)
      }
      case update:Update => if(matchRow(m)){
        buf.clear()
        parseUpdate(m,update,buf)
        processRow(buf,sa)
      }
      case delete:Delete => if(matchRow(m)){
        buf.clear()
        parseDelete(m,delete,buf)
        processRow(buf,sa)
      }
    }
  }

  /**
    * 匹配行与表, 用于过滤数据
    * @param m
    * @return
    */
  def matchRow(m:MutationWithInfo):Boolean = true

  /**
    * 处理数据,将生成的sql语句导入数据库中
    * @param buf
    * @param sa
    */
  def processRow(buf:StringBuilder,sa: StateActor):Unit = {

  }

  /**
    * Whole class is basically a mix of <a href="https://code.google.com/p/open-replicator">open-replicator</a>'s
    * AbstractRowEventParser and MySQLUtils. Main purpose here is to ease rows deserialization.<p>
    *
    * Current {@link ColumnType} to java type mapping is following:
    * <pre>
    * {@link ColumnType#TINY}: Integer
    * {@link ColumnType#SHORT}: Integer
    * {@link ColumnType#LONG}: Integer
    * {@link ColumnType#INT24}: Integer
    * {@link ColumnType#YEAR}: Integer
    * {@link ColumnType#ENUM}: Integer
    * {@link ColumnType#SET}: Long
    * {@link ColumnType#LONGLONG}: Long
    * {@link ColumnType#FLOAT}: Float
    * {@link ColumnType#DOUBLE}: Double
    * {@link ColumnType#BIT}: java.util.BitSet
    * {@link ColumnType#DATETIME}: java.util.Date
    * {@link ColumnType#DATETIME_V2}: java.util.Date
    * {@link ColumnType#NEWDECIMAL}: java.math.BigDecimal
    * {@link ColumnType#TIMESTAMP}: java.sql.Timestamp
    * {@link ColumnType#TIMESTAMP_V2}: java.sql.Timestamp
    * {@link ColumnType#DATE}: java.sql.Date
    * {@link ColumnType#TIME}: java.sql.Time
    * {@link ColumnType#TIME_V2}: java.sql.Time
    * {@link ColumnType#VARCHAR}: String
    * {@link ColumnType#VAR_STRING}: String
    * {@link ColumnType#STRING}: String
    * {@link ColumnType#BLOB}: byte[]
    * {@link ColumnType#GEOMETRY}: byte[]
    * </pre>
    *
    * At the moment {@link ColumnType#GEOMETRY} is unsupported.
    *
    * @param <T> event data this deserializer is responsible for
    * @author <a href="mailto:stanley.shyiko@gmail.com">Stanley Shyiko</a>
    */
  def parseInsert(m:MutationWithInfo,insert:Insert,buf: StringBuilder):Unit = {
    val data = m.message.get.asInstanceOf[ListMap[String, Any]]
    val columns = m.columns.get
    buf.append("insert into ").append(insert.database).append(".").append(insert.tableName).append("(")
    columns.columns.foreach(c => {
      buf.append(c.name).append(",")
    })
    buf.setLength(buf.length - 1)
    buf.append(") values (")
    columns.columns.foreach(c => {
      buf.append(toSqlVar(data.get(c.name))).append(",")
    })
    buf.setLength(buf.length - 1)
    buf.append(")")
  }


  def toSqlVar(v: Any):String = v match {
    case n:Number => n.toString
    case s:String => "'" + s + "'"
    case d:Date => "'" + FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss",TimeZone.getTimeZone("UTC")).format(d) + "'"
    case b:Array[Byte] => "b"+Hex.encodeHexString(b)
    case null => "null"
    case None => "null"
    case Some(x) => toSqlVar(x)
    case x => x.toString
  }


  def parseUpdate(m:MutationWithInfo, update:Update, buf: StringBuilder):Unit = {
    val data = m.message.get.asInstanceOf[ListMap[String, Any]]
    val columns = m.columns.get
    buf.append("update ").append(update.database).append(".").append(update.tableName)
    val rows = update.rows.head
    val oldRows = update.oldRows.head

    for(i <- 0 until rows.size){
      if(rows(i) != oldRows(i)){
        buf.append(" set ").append(columns.columns(i).name).append("=")
        buf.append(toSqlVar(rows(i))).append(",")
      }
    }
    buf.setLength(buf.length - 1)
    val isPrimary = columns.columns.find(_.isPrimary)
    if(isPrimary.isDefined){
      val name = isPrimary.get.name
      buf.append(" where ").append(name).append("=").append(toSqlVar(data.get(name).get))
    }else{//不支持没有主键的表进行更新
      buf.clear()
    }
  }

  def parseDelete(m: MutationWithInfo, delete: Delete, buf: StringBuilder): Unit = {
    val data = m.message.get.asInstanceOf[ListMap[String, Any]]
    val columns = m.columns.get
    val isPrimary = columns.columns.find(_.isPrimary)
    if(isPrimary.isDefined){
       buf.append("delete from ").append(delete.database).append(".").append(delete.tableName)
       buf.append(" where ").append(isPrimary.get.name).append("=").append(toSqlVar(data.get(isPrimary.get.name).get))
    }
  }
}
