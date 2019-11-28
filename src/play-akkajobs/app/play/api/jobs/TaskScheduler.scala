package play.api.jobs

import org.joda.time.DateTime

trait TaskScheduler {
  def runInternal(fireTime: DateTime,msg:AnyRef): Unit
  def stopInternal(fireTime: DateTime,msg:AnyRef): Unit
}