/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *//*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package play.api.rpc.thrift

import org.apache.thrift.protocol.{TMessage, TMessageType}

/**
  * A container of a Thrift message produced by Apache Thrift.
  */
abstract class ThriftMessage (val header: TMessage) {

  override def hashCode: Int = header.hashCode

  override def equals(o: Any): Boolean = {
    if (this == o) return true
    if (o == null || (getClass ne o.getClass)) return false
    header == o.asInstanceOf[ThriftMessage].header
  }

  private def typeStrOnByte(b: Byte) = b match {
    case TMessageType.CALL =>
      "CALL"
    case TMessageType.ONEWAY =>
      "ONEWAY"
    case TMessageType.REPLY =>
      "REPLY"
    case TMessageType.EXCEPTION =>
      "EXCEPTION"
    case _ =>
      "UNKNOWN(" + (b & 0xFF) + ')'
  }

  final def typeStr = typeStrOnByte(header.`type`)
}