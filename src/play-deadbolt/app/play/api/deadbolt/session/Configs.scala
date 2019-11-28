package play.api.deadbolt.session

import java.util.concurrent.TimeUnit

import play.api.Configuration

import scala.concurrent.duration.Duration


class Configs {

  var globalSessionTimeout:Long = Duration.apply(1,TimeUnit.HOURS).toMillis

  var sessionPrefix:String ="session."
}
