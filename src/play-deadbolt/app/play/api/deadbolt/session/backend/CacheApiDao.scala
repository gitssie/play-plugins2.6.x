package play.api.deadbolt.session.backend

import java.io
import java.util.concurrent.TimeUnit

import play.api.cache.{AsyncCacheApi, SyncCacheApi}
import play.api.deadbolt.session.mgt.SessionDAO
import play.api.deadbolt.session.{Configs, Session, UnknownSessionException}

import scala.concurrent.Await
import scala.concurrent.duration.Duration


class CacheApiDao(configs: Configs,cacheApi: AsyncCacheApi) extends SessionDAO {
  val awaitTimeout: Duration = Duration(5,TimeUnit.SECONDS)
  /**
    * Inserts a new Session record into the underling EIS (e.g. Relational database, file system, persistent cache,
    * etc, depending on the DAO implementation).
    * <p/>
    * method executed on the argument must return a valid session identifier.  That is, the following should
    * always be true:
    * <pre>
    * Serializable id = create( session );
    * id.equals( session.getId() ) == true</pre>
    * <p/>
    * Implementations are free to throw any exceptions that might occur due to
    * integrity violation constraints or other EIS related errors.
    *
    * @return the EIS id (e.g. primary key) of the created { @code Session} object.
    */
  override def create(session: Session):Session = {
      val cacheKey: String = storeKey(session.getId)
      cacheApi.set(cacheKey,session,Duration(session.getTimeout,TimeUnit.MILLISECONDS))
      session
  }

  private def storeKey(sessionId: io.Serializable) = {
    val cacheKey = configs.sessionPrefix + sessionId
    cacheKey
  }

  /**
    * Retrieves the session from the EIS uniquely identified by the specified
    * {@code sessionId}.
    *
    * @param sessionId the system-wide unique identifier of the Session object to retrieve from
    *                  the EIS.
    * @return the persisted session in the EIS identified by { @code sessionId}.
    * @throws UnknownSessionException if there is no EIS record for any session with the
    *                                 specified { @code sessionId}
    */
  @throws[UnknownSessionException]
  override def readSession(sessionId: io.Serializable) :Session = {
    val cacheKey: String = storeKey(sessionId)
    Await.result(cacheApi.get[Session](cacheKey),awaitTimeout).getOrElse(null)
  }

  /**
    * Updates (persists) data from a previously created Session instance in the EIS identified by
    * {@code {@link Session#getId() session.getId()}}.  This effectively propagates
    * the data in the argument to the EIS record previously saved.
    * <p/>
    * In addition to UnknownSessionException, implementations are free to throw any other
    * exceptions that might occur due to integrity violation constraints or other EIS related
    * errors.
    *
    * @param session the Session to update
    *                if no existing EIS session record exists with the
    *                identifier of { @link Session#getId() session.getSessionId()}
    */
  override def update(session: Session) = {
    val cacheKey: String = storeKey(session.getId)
    cacheApi.set(cacheKey,session,Duration(session.getTimeout,TimeUnit.MILLISECONDS))
  }

  /**
    * Deletes the associated EIS record of the specified {@code session}.  If there never
    * existed a session EIS record with the identifier of
    * {@link Session#getId() session.getId()}, then this method does nothing.
    *
    * @param session the session to delete.
    */
  override def delete(session: Session) = {
    val cacheKey: String = storeKey(session.getId)
    cacheApi.remove(cacheKey)
  }
}
