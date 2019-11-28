/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *//*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package play.api.deadbolt.session.mgt

import java.io.Serializable

import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import play.api.deadbolt.session._
import play.api.deadbolt.session.backend.MemorySessionDAO

/**
  * Default business-tier implementation of a {@link ValidatingSessionManager}.  All session CRUD operations are
  * delegated to an internal {@link SessionDAO}.
  *
  * @since 0.1
  */

class DefaultSessionManager(var configs: Configs, var sessionDAO: SessionDAO, var sessionFactory: SessionFactory, var idGenerator: SessionIdGenerator) extends AbstractNativeSessionManager {
  private val log = LoggerFactory.getLogger(classOf[DefaultSessionManager])
  private var deleteInvalidSessions = true

  def this(configs: Configs) {
    this(configs, new MemorySessionDAO, new SimpleSessionFactory, new RandomSessionIdGenerator)
  }

  def setSessionDAO(sessionDAO: SessionDAO): Unit = this.sessionDAO = sessionDAO

  def getSessionDAO: SessionDAO = this.sessionDAO

  def getIdGenerator: SessionIdGenerator = idGenerator

  def setIdGenerator(idGenerator: SessionIdGenerator): Unit = this.idGenerator = idGenerator

  /**
    * Returns the {@code SessionFactory} used to generate new {@link Session} instances.  The default instance
    * is a {@link SimpleSessionFactory}.
    *
    * @return the { @code SessionFactory} used to generate new { @link Session} instances.
    * @since 1.0
    */
  def getSessionFactory: SessionFactory = sessionFactory

  /**
    * Sets the {@code SessionFactory} used to generate new {@link Session} instances.  The default instance
    * is a {@link SimpleSessionFactory}.
    *
    * @param sessionFactory the { @code SessionFactory} used to generate new { @link Session} instances.
    * @since 1.0
    */
  def setSessionFactory(sessionFactory: SessionFactory): Unit = this.sessionFactory = sessionFactory

  /**
    * Returns {@code true} if sessions should be automatically deleted after they are discovered to be invalid,
    * {@code false} if invalid sessions will be manually deleted by some process external to Shiro's control.  The
    * default is {@code true} to ensure no orphans exist in the underlying data store.
    * <h4>Usage</h4>
    * It is ok to set this to {@code false} <b><em>ONLY</em></b> if you have some other process that you manage yourself
    * that periodically deletes invalid sessions from the backing data store over time, such as via a Quartz or Cron
    * job.  If you do not do this, the invalid sessions will become 'orphans' and fill up the data store over time.
    * <p/>
    * This property is provided because some systems need the ability to perform querying/reporting against sessions in
    * the data store, even after they have stopped or expired.  Setting this attribute to {@code false} will allow
    * such querying, but with the caveat that the application developer/configurer deletes the sessions themselves by
    * some other means (cron, quartz, etc).
    *
    * @return { @code true} if sessions should be automatically deleted after they are discovered to be invalid,
    *                 { @code false} if invalid sessions will be manually deleted by some process external to Shiro's control.
    * @since 1.0
    */
  def isDeleteInvalidSessions: Boolean = deleteInvalidSessions

  /**
    * Sets whether or not sessions should be automatically deleted after they are discovered to be invalid.  Default
    * value is {@code true} to ensure no orphans will exist in the underlying data store.
    * <h4>WARNING</h4>
    * Only set this value to {@code false} if you are manually going to delete sessions yourself by some process
    * (quartz, cron, etc) external to Shiro's control.  See the
    * {@link #isDeleteInvalidSessions() isDeleteInvalidSessions()} JavaDoc for more.
    *
    * @param deleteInvalidSessions whether or not sessions should be automatically deleted after they are discovered
    *                              to be invalid.
    * @since 1.0
    */
  def setDeleteInvalidSessions(deleteInvalidSessions: Boolean): Unit = this.deleteInvalidSessions = deleteInvalidSessions

  protected def newSessionInstance(context: SessionContext): Session = {
    if (context.getSessionId == null) context.setSessionId(idGenerator.generateId)
    getSessionFactory.createSession(context)
  }

  /**
    * Persists the given session instance to an underlying EIS (Enterprise Information System).  This implementation
    * delegates and calls
    *
    * @param session the Session instance to persist to the underlying EIS.
    */
  protected def create(session: Session): Unit = {
    if(log.isDebugEnabled) log.debug("Creating new EIS record for new session instance [" + session + "]")
    session.setTimeout(configs.globalSessionTimeout)
    sessionDAO.create(session)
  }

  @throws[SessionException]
  override protected def createSession(context: SessionContext): Session = {
    val session = newSessionInstance(context)
    if (log.isDebugEnabled) log.debug("Creating new EIS record for new session instance [" + session + "]")
    session.setTimeout(configs.globalSessionTimeout)
    sessionDAO.create(session)
    session
  }

  @throws[InvalidSessionException]
  override protected def doGetSession(key: SessionKey): Session = {
    val s = retrieveSession(key)
    if (s != null && validate(s, key)){
      s
    }else{
      null
    }
  }

  @throws[InvalidSessionException]
  protected def validate(session: Session, key: SessionKey): Boolean = try{
    doValidate(session)
    true
  }catch {
    case ese: ExpiredSessionException =>
      onExpiration(session, ese, key)
      false
    case ise: InvalidSessionException =>
      onInvalidation(session, ise, key)
      false
    case t:Throwable =>
      if(log.isTraceEnabled()) log.trace("Session with id [{}] has throwable.",key.getSessionId,t)
      false
  }

  protected def onExpiration(s: Session, ese: ExpiredSessionException, key: SessionKey): Unit = {
    if(log.isTraceEnabled()) log.trace("Session with id [{}] has expired.",key.getSessionId)
    try {
      onExpiration(s)
      notifyExpiration(s)
    } finally afterExpired(s)
  }

  protected def afterExpired(session: Session): Unit = {
  }

  protected def onInvalidation(s: Session, ise: InvalidSessionException, key: SessionKey): Unit = {
    if (ise.isInstanceOf[ExpiredSessionException]) {
      onExpiration(s, ise.asInstanceOf[ExpiredSessionException], key)
      return
    }
    if(log.isTraceEnabled) log.trace("Session with id [{}] is invalid.", key.getSessionId)
    try {
      onStop(s)
      notifyStop(s)
    } finally afterStopped(s)
  }

  @throws[InvalidSessionException]
  protected def doValidate(session: Session): Unit = session.validate()

  override protected def onStop(session: Session): Unit = {
    if (session.isInstanceOf[SimpleSession]) {
      val ss = session.asInstanceOf[SimpleSession]
      val stopTs = ss.getStopTimestamp
      ss.setLastAccessTime(stopTs)
    }
    onChange(session)
  }

  override protected def afterStopped(session: Session): Unit = if (isDeleteInvalidSessions) delete(session)

  protected def onExpiration(session: Session): Unit = {
    if (session.isInstanceOf[SimpleSession]) session.asInstanceOf[SimpleSession].setExpired(true)
    onChange(session)
  }

  override protected def onChange(session: Session): Unit = sessionDAO.update(session)

  @throws[UnknownSessionException]
  protected def retrieveSession(sessionKey: SessionKey): Session = {
    val sessionId = getSessionId(sessionKey)
    if (sessionId == null) {
      log.debug("Unable to resolve session ID from SessionKey [{}].  Returning null to indicate a " + "session could not be found.", sessionKey)
      return null
    }
    val s = retrieveSessionFromDataSource(sessionId)
    s
  }

  protected def getSessionId(sessionKey: SessionKey): Serializable = sessionKey.getSessionId

  @throws[UnknownSessionException]
  protected def retrieveSessionFromDataSource(sessionId: Serializable): Session = sessionDAO.readSession(sessionId)

  protected def delete(session: Session): Unit = sessionDAO.delete(session)

  override def destroy(session: Session): Unit = if(session.getId != null) delete(session)
}