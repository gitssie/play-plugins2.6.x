package play.api.jobs

import play.api.inject.Module
import play.api.{Configuration, Environment}

import scala.collection.Seq

/**
  * This is a job Module which loads all classes which implemets {@link AbstractAkkaJob} and adds them to the {@link Scheduler}
  *
  */
class JobModule extends Module {
  override def bindings(environment: Environment, configuration: Configuration) = Seq(
    bind[JobClassLoader].toSelf.eagerly()
  )
}