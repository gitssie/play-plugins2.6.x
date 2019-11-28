package play.api.jobs

/**
  * An {@link Exception} which is thrown in the {@link JobModule} context.
  */
class JobException(message:String,e:Throwable) extends Exception(message,e){
  def this(message: String) = this(message,null)
}