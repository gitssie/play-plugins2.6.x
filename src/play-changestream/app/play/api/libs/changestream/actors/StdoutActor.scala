package play.api.libs.changestream.actors

import play.api.libs.changestream.events.MutationWithInfo

import scala.collection.immutable.ListMap

class StdoutActor extends SyncActor {
  def receive = {
    case MutationWithInfo(mutation, _, _, Some(message: AnyRef)) =>
      val data = message.asInstanceOf[ListMap[String,Any]]
  }
}