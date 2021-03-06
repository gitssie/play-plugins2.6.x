package play.api.libs.changestream

import com.github.shyiko.mysql.binlog.BinaryLogClient.EventListener
import com.github.shyiko.mysql.binlog.event.EventType._
import com.github.shyiko.mysql.binlog.event._
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import play.api.Environment
import play.api.db.Database
import play.api.libs.changestream.actors._
import play.api.libs.changestream.events._
import play.utils.Reflect

import scala.util.Failure

class ChangeStreamEventListener(val config: Config,val ds: Database,cs:ChangeStream,env:Environment) extends EventListener {
  protected val log = LoggerFactory.getLogger(getClass)

  protected val systemDatabases = Seq("information_schema", "mysql", "performance_schema", "sys")
  protected val whitelist: java.util.List[String] = new java.util.LinkedList[String]()
  protected val blacklist: java.util.List[String] = new java.util.LinkedList[String]()

  @volatile
  protected var emitterLoader:SyncActor = new StdoutActor()

  protected lazy val formatterActor = new MapFormatterActor(emitterLoader)
  protected lazy val columnInfoActor = new ColumnInfoActor(formatterActor,ds)
  protected lazy val transactionActor = new TransactionActor(columnInfoActor)
  protected lazy val stateActor = new StateActor(cs)

  setConfig(config)

  /** Allows the configuration for the listener object to be set on startup.
    * The listener will look for whitelist, blacklist, and emitter settings.
    *
    * @param config
    */
  def setConfig(config: Config) = {
    whitelist.clear()
    blacklist.clear()

    if(config.hasPath("whitelist")) {
      config.getString("whitelist").split(',').foreach(whitelist.add(_))
      log.info(s"Using event whitelist: ${whitelist}")
    }
    else if(config.hasPath("blacklist")) {
      config.getString("blacklist").split(',').foreach(blacklist.add(_))
      log.info(s"Using event blacklist: ${blacklist}")
    }

    if(config.hasPath("emitter")) {
      val classString = config.getString("emitter")

      try {
        val emitterConstructor = Reflect.getClass[SyncActor](classString,env.classLoader).getDeclaredConstructors.head
        lazy val actorInstance = emitterConstructor.newInstance(config,stateActor).asInstanceOf[SyncActor]
        setEmitterLoader(actorInstance)
      }
      catch {
        case e: ClassNotFoundException =>
          log.error(s"Couldn't load emitter class ${classString} (ClassNotFoundException), using the default emitter.")
      }
    }
  }

  /** Allows the emitter/producer actor to be configured at runtime.
    *
    * Note: you must initialize the emitter before the first event arrives, or it will be ignored
    *
    * @param loader A lamda that returns your actor ref
    */
  def setEmitterLoader(loader:SyncActor) = emitterLoader = loader

  /** Sends binlog events to the appropriate changestream actor.
    *
    * @param binaryLogEvent The binlog event
    */
  def onEvent(binaryLogEvent: Event) = {
    log.debug(s"Received event: ${binaryLogEvent}")
    val changeEvent = getChangeEvent(binaryLogEvent)

    try{
      changeEvent match {
        case Some(e: TransactionEvent)  => transactionActor.receive(e)
        case Some(e: MutationEvent)     => transactionActor.receive(MutationWithInfo(e))
        case Some(e: AlterTableEvent)   => columnInfoActor.receive(e)
        case None =>
          log.debug(s"Ignoring ${binaryLogEvent.getHeader[EventHeaderV4].getEventType} event.")
      }
    }catch{
      case t:Throwable => stateActor.receive(Failure(t))
    }
  }

  /** Returns the appropriate ChangeEvent case object given a binlog event object.
    *
    * @param event The java binlog listener event
    * @return The resulting ChangeEvent
    */
  def getChangeEvent(event: Event): Option[ChangeEvent] = {
    val header = event.getHeader[EventHeaderV4]

    header.getEventType match {
      case eventType if EventType.isRowMutation(eventType) =>
        getMutationEvent(event, header)

      case GTID =>
        Some(Gtid(event.getData[GtidEventData].getGtid))

      case XID =>
        Some(CommitTransaction)

      case QUERY =>
        parseQueryEvent(event.getData[QueryEventData])

      case FORMAT_DESCRIPTION =>
        val data = event.getData[FormatDescriptionEventData]
        log.info(s"Server version: ${data.getServerVersion}, binlog version: ${data.getBinlogVersion}")
        None

      // Known events that are safe to ignore
      case PREVIOUS_GTIDS => None
      case ROTATE => None
      case ROWS_QUERY => None
      case TABLE_MAP => None
      case ANONYMOUS_GTID => None
      case STOP => None

      case _ =>
        val message = s"Received unknown message: ${event}"
        log.error(message)
        throw new Exception(message)
    }
  }

  protected def getMutationEvent(event: Event, header: EventHeaderV4): Option[MutationEvent] = {
    val mutation = header.getEventType match {
      case e if EventType.isWrite(e) =>
        event.getData[Insert].copy(timestamp = header.getTimestamp)

      case e if EventType.isUpdate(e) =>
        event.getData[Update].copy(timestamp = header.getTimestamp)

      case e if EventType.isDelete(e) =>
        event.getData[Delete].copy(timestamp = header.getTimestamp)
    }

    shouldIgnore(mutation) match {
      case true =>
        log.debug(s"Ignoring event for table ${mutation.database}.${mutation.tableName}.")
        None
      case false =>
        Some(mutation)
    }
  }

  /** Returns a ChangeEvent case class instance representing the change indicated by
    * the given binlog QUERY event (either BEGIN, COMMIT, ROLLBACK, or ALTER...).
    *
    * @param queryData The QUERY event data
    * @return
    */
  protected def parseQueryEvent(queryData: QueryEventData): Option[ChangeEvent] = {
    queryData.getSql match {
      case sql if sql matches "(?i)^begin" =>
        Some(BeginTransaction)

      case sql if sql matches "(?i)^commit" =>
        Some(CommitTransaction)

      case sql if sql matches "(?i)^rollback" =>
        Some(RollbackTransaction)

      case sql if sql matches "(?i)^alter.*" =>
        /** Not(space, dot)+ OR backtick + Not(backtick, dot) + backtick OR "Not(", dot)" **/
        /** Does not currently support spaces or backticks in table or db names **/
        val dbOrTableName = "([^\\s\\.]+|`[^`\\.]+`|\"[^\"\\.]\")"
        val identifierRegex = s"(?i)^alter\\s+(ignore\\s+)?table\\s+($dbOrTableName(\\.$dbOrTableName)?).*".r

        var maybeIdentifier:Option[(String, String)] = None
        for { p <- identifierRegex findAllIn sql } p match {
          case identifierRegex(_, _, first, _, second) =>
            maybeIdentifier = Some(second match {
              case null => (queryData.getDatabase, unescapeIdentifier(first)) //scalastyle:ignore
              case _ => (unescapeIdentifier(first), unescapeIdentifier(second))
            })
        }

        maybeIdentifier.map({
          case (db, table) => Some(AlterTableEvent(db.toLowerCase, table.toLowerCase, sql))
        }).getOrElse(
          None
        )
      case sql =>
        None
    }
  }

  protected def unescapeIdentifier(escaped: String) = escaped.charAt(0) match {
    case '`' => escaped.substring(1, escaped.length - 1).replace("``", "`")
    case '"' => escaped.substring(1, escaped.length - 1).replace("\"\"", "\"")
    case _ => escaped
  }

  private def shouldIgnore(info: MutationEvent) = {
    if(systemDatabases.contains(info.database)) {
      true
    }
    else if(!whitelist.isEmpty) {
      tableMissingFromWhitelist(info.database, info.tableName)
    }
    else if(!blacklist.isEmpty) {
      tableInBlacklist(info.database, info.tableName)
    }
    else {
      false
    }
  }

  private def tableInBlacklist(database: String, table: String) = {
    tableInList(database, table, blacklist)
  }

  private def tableMissingFromWhitelist(database: String, table: String) = {
    !tableInList(database, table, whitelist)
  }

  private def tableInList(database: String, table: String, list: java.util.List[String]) = {
    if(list.isEmpty) {
      false
    }
    else if(list.contains(s"${database}.*") || list.contains(s"${database}.${table}")) {
      true
    }
    else {
      false
    }
  }
}