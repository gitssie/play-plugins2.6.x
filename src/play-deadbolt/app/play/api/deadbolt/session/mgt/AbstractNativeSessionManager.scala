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

import java.util
import java.util.{Collections, Date}

import org.slf4j.LoggerFactory
import play.api.deadbolt.session._

import scala.collection.JavaConverters._
import scala.collection.mutable
/**
  * Abstract implementation supporting the {@link NativeSessionManager NativeSessionManager} interface, supporting
  * {@link SessionListener SessionListener}s and application of the
  *
  * @since 1.0
  */
abstract class AbstractNativeSessionManager extends NativeSessionManager {
  private val log = LoggerFactory.getLogger(classOf[NativeSessionManager])
  private var listeners = mutable.Buffer.empty[SessionListener]

  def setSessionListeners(listeners: util.Collection[SessionListener]): Unit = this.listeners = new util.ArrayList[SessionListener].asScala

  def getSessionListeners: Seq[SessionListener] = this.listeners

  override def start(context: SessionContext): Session = {
    val session = createSession(context)
    onStart(session, context)
    notifyStart(session)
    session
  }

  /**
    * Creates a new {@code Session Session} instance based on the specified (possibly {@code null})
    * initialization data.  Implementing classes must manage the persistent state of the returned session such that it
    * could later be acquired via the {@link #getSession(SessionKey)} method.
    *
    * @param context the initialization data that can be used by the implementation or underlying
    *                { @link SessionFactory} when instantiating the internal { @code Session} instance.
    *                                                                                caller to start sessions.
    */
  @throws[SessionException]
  protected def createSession(context: SessionContext): Session

  /**
    * Template method that allows subclasses to react to a new session being created.
    * <p/>
    * This method is invoked <em>before</em> any session listeners are notified.
    *
    * @param session the session that was just { @link #createSession created}.
    * @param context the { @link SessionContext SessionContext} that was used to start the session.
    */
  protected def onStart(session: Session, context: SessionContext): Unit = {
  }

  @throws[SessionException]
  override def getSession(key: SessionKey): Session = {
    val session = lookupSession(key)
    session
  }

  @throws[SessionException]
  private def lookupSession(key: SessionKey) = {
    if (key == null) throw new NullPointerException("SessionKey argument cannot be null.")
    doGetSession(key)
  }

  @throws[SessionException]
  private def lookupRequiredSession(key: SessionKey) = {
    val session = lookupSession(key)
    if (session == null) {
      val msg = "Unable to locate required Session instance based on SessionKey [" + key + "]."
      throw new UnknownSessionException(msg)
    }
    session
  }

  @throws[InvalidSessionException]
  protected def doGetSession(key: SessionKey): Session

  /**
    * Returns the session instance to use to pass to registered {@code SessionListener}s for notification
    * that the session has been invalidated (stopped or expired).
    * <p/>
    * The default implementation returns an {@link ImmutableProxiedSession ImmutableProxiedSession} instance to ensure
    * that the specified {@code session} argument is not modified by any listeners.
    *
    * @param session the { @code Session} object being invalidated.
    * @return the { @code Session} instance to use to pass to registered { @code SessionListener}s for notification.
    */
  protected def beforeInvalidNotification(session: Session) = new ImmutableProxiedSession(session)

  /**
    * Notifies any interested {@link SessionListener}s that a Session has started.  This method is invoked
    * <em>after</em> the {@link #onStart onStart} method is called.
    *
    * @param session the session that has just started that will be delivered to any
    *                { @link #setSessionListeners(java.util.Collection) registered} session listeners.
    */
  protected def notifyStart(session: Session): Unit = {
    for (listener <- this.listeners) {
      listener.onStart(session)
    }
  }

  protected def notifyStop(session: Session): Unit = {
    val forNotification = beforeInvalidNotification(session)
    for (listener <- this.listeners) {
      listener.onStop(forNotification)
    }
  }

  protected def notifyExpiration(session: Session): Unit = {
    val forNotification = beforeInvalidNotification(session)
    for (listener <- this.listeners) {
      listener.onExpiration(forNotification)
    }
  }

  override def getStartTimestamp(key: SessionKey): Date = lookupRequiredSession(key).getStartTimestamp

  override def getLastAccessTime(key: SessionKey): Date = lookupRequiredSession(key).getLastAccessTime

  @throws[InvalidSessionException]
  override def getTimeout(key: SessionKey): Long = lookupRequiredSession(key).getTimeout

  @throws[InvalidSessionException]
  override def setTimeout(key: SessionKey, maxIdleTimeInMillis: Long): Unit = {
    val s = lookupRequiredSession(key)
    s.setTimeout(maxIdleTimeInMillis)
    onChange(s)
  }

  @throws[InvalidSessionException]
  override def touch(session:Session): Unit = {
    session.touch()
    onChange(session)
  }

  override def getHost(key: SessionKey): String = lookupRequiredSession(key).getHost

  override def getAttributeKeys(key: SessionKey): util.Collection[String] = {
    val c = lookupRequiredSession(key).getAttributeKeys
    c
  }

  @throws[InvalidSessionException]
  override def getAttribute(sessionKey: SessionKey, attributeKey: String): AnyRef = lookupRequiredSession(sessionKey).getAttribute(attributeKey)

  @throws[InvalidSessionException]
  override def setAttribute(sessionKey: SessionKey, attributeKey: String, value: AnyRef): Unit = if (value == null) removeAttribute(sessionKey, attributeKey)
  else {
    val s = lookupRequiredSession(sessionKey)
    s.setAttribute(attributeKey, value)
    onChange(s)
  }

  @throws[InvalidSessionException]
  override def removeAttribute(sessionKey: SessionKey, attributeKey: String): AnyRef = {
    val s = lookupRequiredSession(sessionKey)
    val removed = s.removeAttribute(attributeKey)
    if (removed != null) onChange(s)
    removed
  }

  override def isValid(key: SessionKey): Boolean = try {
    checkValid(key)
    true
  } catch {
    case e: InvalidSessionException =>
      false
  }

  @throws[InvalidSessionException]
  override def stop(key: SessionKey): Unit = {
    val session = lookupRequiredSession(key)
    try {
      if (log.isDebugEnabled) log.debug("Stopping session with id [" + session.getId + "]")
      session.stop()
      onStop(session, key)
      notifyStop(session)
    } finally afterStopped(session)
  }

  protected def onStop(session: Session, key: SessionKey): Unit = onStop(session)

  protected def onStop(session: Session): Unit = onChange(session)

  protected def afterStopped(session: Session): Unit = {}

  @throws[InvalidSessionException]
  override def checkValid(key: SessionKey): Unit = { //just try to acquire it.  If there is a problem, an exception will be thrown:
    lookupRequiredSession(key)
  }

  protected def onChange(s: Session): Unit = {}
}