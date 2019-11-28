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
package play.api.deadbolt.session.backend

import java.io.Serializable

import play.api.deadbolt.session.mgt.SessionDAO
import play.api.deadbolt.session.{Session, UnknownSessionException}

import scala.collection.JavaConverters._
import scala.collection.mutable
/**
  * Simple memory-based implementation of the SessionDAO that stores all of its sessions in an in-memory
  * {@link ConcurrentMap}.  <b>This implementation does not page to disk and is therefore unsuitable for applications
  * that could experience a large amount of sessions</b> and would therefore cause {@code OutOfMemoryException}s.  It is
  * <em>not</em> recommended for production use in most environments.
  * <h2>Memory Restrictions</h2>
  */
class MemorySessionDAO extends SessionDAO {
  private val sessions:mutable.Map[Serializable,Session] = new java.util.concurrent.ConcurrentHashMap[Serializable,Session]().asScala

  protected def storeSession(id: Serializable, session: Session): Session = {
    if (id == null) throw new NullPointerException("id argument cannot be null.")
    sessions.put(id, session)
    session
  }

  override def create(session: Session): Session = {
    storeSession(session.getId, session)
    session
  }

  @throws[UnknownSessionException]
  override def readSession(sessionId: Serializable): Session = sessions.getOrElse(sessionId,null)

  @throws[UnknownSessionException]
  override def update(session: Session): Unit = storeSession(session.getId, session)

  override def delete(session: Session): Unit = {
    if (session == null) throw new NullPointerException("session argument cannot be null.")
    val id = session.getId
    if (id != null) sessions.remove(id)
  }
}