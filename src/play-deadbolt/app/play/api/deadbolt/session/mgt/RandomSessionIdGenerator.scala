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
import java.security.{NoSuchAlgorithmException, SecureRandom}
import java.util.Random

import org.slf4j.LoggerFactory

/**
  * Generates session IDs by using a {@link Random} instance to generate random IDs. The default {@code Random}
  * implementation is a {@link java.security.SecureRandom SecureRandom} with the {@code SHA1PRNG} algorithm.
  *
  * @since 1.0
  */
class RandomSessionIdGenerator extends SessionIdGenerator {
  private val log = LoggerFactory.getLogger(classOf[RandomSessionIdGenerator])
  private var random:Random = null

  try {
    random = java.security.SecureRandom.getInstance("SHA1PRNG")
  }
  catch {
    case e: NoSuchAlgorithmException =>
      log.debug("The SecureRandom SHA1PRNG algorithm is not available on the current platform.  Using the " + "platform's default SecureRandom algorithm.", e)
      this.random = new SecureRandom
  }

  def getRandom: Random = this.random

  def setRandom(random: Random): Unit = this.random = random

  /**
    * Returns the String value of the configured {@link Random}'s {@link Random#nextLong() nextLong()} invocation.
    */
  override def generateId: Serializable = getRandom.nextLong().toString
}