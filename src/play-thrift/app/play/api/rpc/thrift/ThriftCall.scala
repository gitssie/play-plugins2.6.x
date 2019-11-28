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

import com.google.common.base.MoreObjects
import org.apache.thrift.protocol.{TMessage, TMessageType}
import org.apache.thrift.{TBase, TFieldIdEnum}

final class ThriftCall(header: TMessage, val args: TBase[_,_]) extends ThriftMessage(header) {

  if (header.`type` != TMessageType.CALL && header.`type` != TMessageType.ONEWAY) throw new IllegalArgumentException("header.type: " + typeStr(header.`type`) + " (expected: CALL or ONEWAY)")

  override def equals(o: Any): Boolean = {
    if (this == o) return true
    if (o == null || (getClass ne o.getClass)) return false
    super.equals(o) && args == o.asInstanceOf[ThriftCall].args
  }

  override def hashCode: Int = 31 * super.hashCode + args.hashCode

  override def toString: String = MoreObjects.toStringHelper(this).add("seqId", header.seqid).add("type", typeStr).add("name", header.name).add("args", args).toString
}