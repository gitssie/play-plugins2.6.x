package play.api.libs.changestream.actors

import java.util.UUID

import org.slf4j.LoggerFactory
import play.api.libs.changestream.events.{MutationWithInfo, _}

class TransactionActor(nextHop:SyncActor) extends SyncActor {
  protected val log = LoggerFactory.getLogger(getClass)

  /** Mutable State! */
  protected var mutationCount: Long = 1
  protected var currentGtid: Option[String] = None
  protected var previousMutation: Option[MutationWithInfo] = None

  def receive = {
    case BeginTransaction =>
      log.debug(s"Received BeginTransacton")
      mutationCount = 1
      currentGtid = Some(UUID.randomUUID.toString)
      previousMutation = None

    case Gtid(guid) =>
      log.debug(s"Received GTID for transaction: ${guid}")
      currentGtid = Some(guid)

    case event: MutationWithInfo =>
      log.debug(s"Received Mutation for tableId: ${event.mutation.tableId}")
      currentGtid match {
        case None =>
          nextHop.receive(event)
        case Some(gtid) =>
          previousMutation.foreach { mutation =>
            nextHop.receive(mutation)
          }
          previousMutation = Some(event.copy(
            transaction = Some(TransactionInfo(
              gtid = gtid,
              currentRow = mutationCount
            ))
          ))
          mutationCount += event.mutation.rows.length
      }

    case _: TransactionEvent =>
      log.debug(s"Received Commit/Rollback")
      previousMutation.foreach { mutation =>
        nextHop.receive(mutation.copy(
          transaction = mutation.transaction.map { txInfo =>
            txInfo.copy(lastMutationInTransaction = true)
          }
        ))
      }
      mutationCount = 1
      currentGtid = None
      previousMutation = None
  }
}