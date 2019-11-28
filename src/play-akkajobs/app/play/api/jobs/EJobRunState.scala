package play.api.jobs

/**
  * This is the state of the job the job had on its run
  *
  */
object EJobRunState extends Enumeration {
  type State = Value
  val DISABLED, RUNNING, SCHEDULED, STOPPED, KILLED, ERROR = Value
}