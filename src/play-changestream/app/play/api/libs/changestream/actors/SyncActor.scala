package play.api.libs.changestream.actors

import akka.actor.Actor

trait SyncActor {
  def receive: Actor.Receive
}

