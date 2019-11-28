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
import java.text.DateFormat
import java.util
import java.util.{Collections, Date}

import play.api.deadbolt.session.{ExpiredSessionException, InvalidSessionException, Session, StoppedSessionException}

@SerialVersionUID(-7125642695178165650L)
class SimpleSession extends Session with Serializable {

  private var id: Serializable = null
  private var startTimestamp: Date = new Date()
  private var stopTimestamp: Date = null
  private var lastAccessTime: Date = startTimestamp
  private var timeout: Long = 0L
  private var expired: Boolean = false
  private var host: String  = null
  private var attributes: util.Map[String, AnyRef] = new util.HashMap()

  def this(host: String) {
    this()
    this.host = host
  }

  override def getId: Serializable = this.id

  def setId(id: Serializable): Unit = this.id = id

  override def getStartTimestamp: Date = startTimestamp

  def setStartTimestamp(startTimestamp: Date): Unit = this.startTimestamp = startTimestamp

  /**
    * Returns the time the session was stopped, or <tt>null</tt> if the session is still active.
    * <p/>
    * A session may become stopped under a number of conditions:
    * <ul>
    * <li>If the user logs out of the system, their current session is terminated (released).</li>
    * <li>If the session expires</li>
    * <li>The application explicitly calls {@link #stop()}</li>
    * <li>If there is an internal system error and the session state can no longer accurately
    * reflect the user's behavior, such in the case of a system crash</li>
    * </ul>
    * <p/>
    * Once stopped, a session may no longer be used.  It is locked from all further activity.
    *
    * @return The time the session was stopped, or <tt>null</tt> if the session is still
    *         active.
    */
  def getStopTimestamp: Date = stopTimestamp

  def setStopTimestamp(stopTimestamp: Date): Unit = this.stopTimestamp = stopTimestamp

  override def getLastAccessTime: Date = lastAccessTime

  def setLastAccessTime(lastAccessTime: Date): Unit = this.lastAccessTime = lastAccessTime

  /**
    * Returns true if this session has expired, false otherwise.  If the session has
    * expired, no further user interaction with the system may be done under this session.
    *
    * @return true if this session has expired, false otherwise.
    */
  def isExpired: Boolean = expired

  def setExpired(expired: Boolean): Unit = this.expired = expired

  override def getTimeout: Long = timeout

  override def setTimeout(timeout: Long): Unit = this.timeout = timeout

  override def getHost: String = host

  def setHost(host: String): Unit = this.host = host

  def getAttributes: util.Map[String, AnyRef] = attributes

  def setAttributes(attributes: util.Map[String, AnyRef]): Unit = this.attributes = attributes

  override def touch(): Unit = this.lastAccessTime = new Date

  override def stop(): Unit = if (this.stopTimestamp == null) this.stopTimestamp = new Date

  protected def isStopped: Boolean = getStopTimestamp != null

  protected def expire(): Unit = {
    stop()
    this.expired = true
  }

  /**
    * @since 0.9
    */
  override def isValid: Boolean = !isStopped && !isExpired

  /**
    * Determines if this session is expired.
    *
    * @return true if the specified session has expired, false otherwise.
    */
  protected def isTimedOut: Boolean = {
    if (isExpired) return true
    val timeout = getTimeout
    if (timeout >= 0l) {
      val lastAccessTime = getLastAccessTime
      if (lastAccessTime == null) {
        val msg = "session.lastAccessTime for session with id [" + getId + "] is null.  This value must be set at " + "least once, preferably at least upon instantiation.  Please check the " + getClass.getName + " implementation and ensure " + "this value will be set (perhaps in the constructor?)"
        throw new IllegalStateException(msg)
      }
      // Calculate at what time a session would have been last accessed
      // for it to be expired at this point.  In other words, subtract
      // from the current time the amount of time that a session can
      // be inactive before expiring.  If the session was last accessed
      // before this time, it is expired.
      val expireTimeMillis = System.currentTimeMillis - timeout
      val expireTime = new Date(expireTimeMillis)
      return lastAccessTime.before(expireTime)
    }
    false
  }

  @throws[InvalidSessionException]
  override def validate(): Unit = { //check for stopped:
    if (isStopped) { //timestamp is set, so the session is considered stopped:
      val msg = "Session with id [" + getId + "] has been " + "explicitly stopped.  No further interaction under this session is " + "allowed."
      throw new StoppedSessionException(msg)
    }
    //check for expiration
    if (isTimedOut) {
      expire()
      //throw an exception explaining details of why it expired:
      val lastAccessTime = getLastAccessTime
      val timeout = getTimeout
      val sessionId = getId
      val df = DateFormat.getInstance
      val msg = "Session with id [" + sessionId + "] has expired. " + "Last access time: " + df.format(lastAccessTime) + ".  Current time: " + df.format(new Date) + ".  Session timeout is set to " + timeout
      throw new ExpiredSessionException(msg)
    }
  }

  private def getAttributesLazy = {
    var attributes = getAttributes
    if (attributes == null) {
      attributes = new util.HashMap[String, AnyRef]
      setAttributes(attributes)
    }
    attributes
  }

  @throws[InvalidSessionException]
  override def getAttributeKeys: util.Collection[String] = {
    val attributes = getAttributes
    if (attributes == null) return Collections.emptySet[String]
    attributes.keySet
  }

  override def getAttribute(key: String): AnyRef = {
    val attributes = getAttributes
    if (attributes == null) return null
    attributes.get(key)
  }

  override def setAttribute(key: String, value: AnyRef): Unit = if (value == null) removeAttribute(key)
  else attributes.put(key, value)

  override def removeAttribute(key: String): AnyRef = {
    val attributes = getAttributes
    if (attributes == null) null
    else attributes.remove(key)
  }

  /**
    * Returns {@code true} if the specified argument is an {@code instanceof} {@code SimpleSession} and both
    * {@link #getId() id}s are equal.  If the argument is a {@code SimpleSession} and either 'this' or the argument
    * does not yet have an ID assigned, the value of {@link #onEquals(SimpleSession) onEquals} is returned, which
    * does a necessary attribute-based comparison when IDs are not available.
    * <p/>
    * Do your best to ensure {@code SimpleSession} instances receive an ID very early in their lifecycle to
    * avoid the more expensive attributes-based comparison.
    *
    * @param obj the object to compare with this one for equality.
    * @return { @code true} if this object is equivalent to the specified argument, { @code false} otherwise.
    */
  override def equals(obj: Any): Boolean = {
    if (this == obj) return true
    if (obj.isInstanceOf[SimpleSession]) {
      val other = obj.asInstanceOf[SimpleSession]
      val thisId = getId
      val otherId = other.getId
      if (thisId != null && otherId != null) return thisId == otherId
      else { //fall back to an attribute based comparison:
        return onEquals(other)
      }
    }
    false
  }

  /**
    * Provides an attribute-based comparison (no ID comparison) - incurred <em>only</em> when 'this' or the
    * session object being compared for equality do not have a session id.
    *
    * @param ss the SimpleSession instance to compare for equality.
    * @return true if all the attributes, except the id, are equal to this object's attributes.
    * @since 1.0
    */
  protected def onEquals(ss: SimpleSession): Boolean = (if (getStartTimestamp != null) getStartTimestamp == ss.getStartTimestamp
  else ss.getStartTimestamp == null) && (if (getStopTimestamp != null) getStopTimestamp == ss.getStopTimestamp
  else ss.getStopTimestamp == null) && (if (getLastAccessTime != null) getLastAccessTime == ss.getLastAccessTime
  else ss.getLastAccessTime == null) && (getTimeout == ss.getTimeout) && (isExpired == ss.isExpired) && (if (getHost != null) getHost == ss.getHost
  else ss.getHost == null) && (if (getAttributes != null) getAttributes == ss.getAttributes
  else ss.getAttributes == null)

  /**
    * Returns the hashCode.  If the {@link #getId() id} is not {@code null}, its hashcode is returned immediately.
    * If it is {@code null}, an attributes-based hashCode will be calculated and returned.
    * <p/>
    * Do your best to ensure {@code SimpleSession} instances receive an ID very early in their lifecycle to
    * avoid the more expensive attributes-based calculation.
    *
    * @return this object's hashCode
    * @since 1.0
    */
  override def hashCode: Int = {
    val id = getId
    if (id != null) return id.hashCode
    var hashCode = if (getStartTimestamp != null) getStartTimestamp.hashCode
    else 0
    hashCode = 31 * hashCode + (if (getStopTimestamp != null) getStopTimestamp.hashCode
    else 0)
    hashCode = 31 * hashCode + (if (getLastAccessTime != null) getLastAccessTime.hashCode
    else 0)
    hashCode = 31 * hashCode + Math.max(getTimeout, 0).hashCode
    hashCode = 31 * hashCode + isExpired.hashCode
    hashCode = 31 * hashCode + (if (getHost != null) getHost.hashCode
    else 0)
    hashCode = 31 * hashCode + (if (getAttributes != null) getAttributes.hashCode
    else 0)
    hashCode
  }

  /**
    * Returns the string representation of this SimpleSession, equal to
    * <code>getClass().getName() + &quot;,id=&quot; + getId()</code>.
    *
    * @return the string representation of this SimpleSession, equal to
    *         <code>getClass().getName() + &quot;,id=&quot; + getId()</code>.
    * @since 1.0
    */
  override def toString: String = {
    val sb = new StringBuilder
    sb.append(getClass.getName).append(",id=").append(getId)
    sb.toString
  }
}