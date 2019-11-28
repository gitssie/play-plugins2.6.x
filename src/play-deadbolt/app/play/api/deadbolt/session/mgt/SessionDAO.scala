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

import play.api.deadbolt.session.Session
import play.api.deadbolt.session.UnknownSessionException
import java.io.Serializable
import java.util

/**
  * Data Access Object design pattern specification to enable {@link Session} access to an
  * EIS (Enterprise Information System).  It provides your four typical CRUD methods:
  * {@link #create}, {@link #readSession(java.io.Serializable)}
  * be as efficient as possible, especially if there are thousands of active sessions.  Large scale/high performance
  * implementations will often return a subset of the total active sessions and perform validation a little more
  * frequently, rather than return a massive set and infrequently validate.
  *
  * @since 0.1
  */
trait SessionDAO {
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
    def create(session: Session): Session

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
  def readSession(sessionId: Serializable): Session

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
  @throws[UnknownSessionException]
  def update(session: Session): Unit

  /**
    * Deletes the associated EIS record of the specified {@code session}.  If there never
    * existed a session EIS record with the identifier of
    * {@link Session#getId() session.getId()}, then this method does nothing.
    *
    * @param session the session to delete.
    */
  def delete(session: Session): Unit
}