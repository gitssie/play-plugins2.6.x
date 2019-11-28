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

class SimpleSessionFactory extends SessionFactory {
  /**
    * Creates a new {@link SimpleSession SimpleSession} instance retaining the context's
    * {@link SessionContext#getHost() host} if one can be found.
    *
    * @param initData the initialization data to be used during { @link Session} creation.
    * @return a new { @link SimpleSession SimpleSession} instance
    */
    override def createSession(initData: SessionContext): Session = {
      val session: SimpleSession = new SimpleSession
      if (initData != null) {
        session.setId(initData.getSessionId)
        session.setHost(initData.getHost)
        session.setAttributes(initData.context)
      }
      session
    }
}