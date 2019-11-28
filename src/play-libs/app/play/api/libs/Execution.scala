package play.api.libs

import java.util.concurrent.ForkJoinPool

import play.api.Play
import play.core.j.HttpExecutionContext

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

/**
  * Provides access to Play's internal ExecutionContext.
  */
object Execution {

  def internalContext: ExecutionContextExecutor = {
    Play.privateMaybeApplication match {
      case None => common
      case Some(app) => app.actorSystem.dispatcher
    }
  }

  def promiseContext: ExecutionContextExecutor = {
    Play.privateMaybeApplication match {
      case None => common
      case Some(app) => {
        val id = "play.promise-dispatcher"
        if(app.actorSystem.dispatchers.hasDispatcher(id)){
          app.actorSystem.dispatchers.lookup(id)
        }else{
          app.actorSystem.dispatcher
        }
      }
    }
  }

  def httpPromiseContext = HttpExecutionContext.fromThread(promiseContext);

  def httpInternalContext= HttpExecutionContext.fromThread(internalContext);
  /**
    * Use this as a fallback when the application is unavailable.
    * The ForkJoinPool implementation promises to create threads on-demand
    * and clean them up when not in use (standard is when idle for 2
    * seconds).
    */
  private val common = ExecutionContext.fromExecutor(new ForkJoinPool())

}
