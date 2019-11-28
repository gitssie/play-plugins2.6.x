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
package play.api.deadbolt.session

import java.io.Serializable
import java.util
import java.util.Date

/**
  * Simple <code>Session</code> implementation that immediately delegates all corresponding calls to an
  * underlying proxied session instance.
  * <p/>
  * This class is mostly useful for framework subclassing to intercept certain <code>Session</code> calls
  * and perform additional logic.
  *
  * @since 0.9
  */
class ProxiedSession(val delegate: Session) extends Session {

  if (delegate == null) throw new IllegalArgumentException("Target session to proxy cannot be null.")

  /**
    * Immediately delegates to the underlying proxied session.
    */
  override def getId: Serializable = delegate.getId

  override def getStartTimestamp: Date = delegate.getStartTimestamp

  override def getLastAccessTime: Date = delegate.getLastAccessTime

  @throws[InvalidSessionException]
  override def getTimeout: Long = delegate.getTimeout

  @throws[InvalidSessionException]
  override def setTimeout(maxIdleTimeInMillis: Long): Unit = delegate.setTimeout(maxIdleTimeInMillis)

  override def getHost: String = delegate.getHost

  @throws[InvalidSessionException]
  override def touch(): Unit = delegate.touch()

  @throws[InvalidSessionException]
  override def stop(): Unit = delegate.stop()

  @throws[InvalidSessionException]
  override def getAttributeKeys: util.Collection[String] = delegate.getAttributeKeys

  @throws[InvalidSessionException]
  override def getAttribute(key: String): AnyRef = delegate.getAttribute(key)

  @throws[InvalidSessionException]
  override def setAttribute(key: String, value: AnyRef): Unit = delegate.setAttribute(key, value)

  @throws[InvalidSessionException]
  override def removeAttribute(key: String): AnyRef = delegate.removeAttribute(key)

  @throws[InvalidSessionException]
  override def validate(): Unit = delegate.validate()

  override def isValid = delegate.isValid
}