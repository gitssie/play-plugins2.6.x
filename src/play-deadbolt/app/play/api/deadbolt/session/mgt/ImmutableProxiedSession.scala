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

import play.api.deadbolt.session.InvalidSessionException
import play.api.deadbolt.session.ProxiedSession
import play.api.deadbolt.session.Session

/**
  * Implementation of the {@link Session Session} interface that proxies another <code>Session</code>, but does not
  * allow any 'write' operations to the underlying session. It allows 'read' operations only.
  * <p/>
  * The <code>Session</code> write operations are defined as follows.  A call to any of these methods on this
  * proxy will immediately result in an {@link InvalidSessionException} being thrown:
  * <ul>
  * Any other method invocation not listed above will result in a corresponding call to the underlying <code>Session</code>.
  *
  * @since 0.9
  */
class ImmutableProxiedSession(val target: Session) extends ProxiedSession(target) {
  /**
    * Simply throws an <code>InvalidSessionException</code> indicating that this proxy is immutable.  Used
    * only in the Session's 'write' methods documented in the top class-level JavaDoc.
    *
    * @throws InvalidSessionException in all cases - used by the Session 'write' method implementations.
    */
  @throws[InvalidSessionException]
  protected def throwImmutableException(): Unit = {
    val msg = "This session is immutable and read-only - it cannot be altered.  This is usually because " + "the session has been stopped or expired already."
    throw new InvalidSessionException(msg)
  }

  /**
    * Immediately {@link #throwImmutableException() throws} an <code>InvalidSessionException</code> in all
    * cases because this proxy is immutable.
    */
  @throws[InvalidSessionException]
  override def setTimeout(maxIdleTimeInMillis: Long): Unit = throwImmutableException()

  @throws[InvalidSessionException]
  override def touch(): Unit = throwImmutableException()

  @throws[InvalidSessionException]
  override def stop(): Unit = throwImmutableException()

  @throws[InvalidSessionException]
  override def setAttribute(key: String, value: AnyRef): Unit = throwImmutableException()

  @throws[InvalidSessionException]
  override def removeAttribute(key: String): AnyRef = {
    throwImmutableException()
    null
  }
}