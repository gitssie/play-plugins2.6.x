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

import java.util.Objects

import com.google.common.base.MoreObjects
import org.apache.thrift.protocol.TMessage
import org.apache.thrift.{TApplicationException, TBase, TFieldIdEnum}

final class ThriftReply(header: TMessage,val result: TBase[_,_],val exception: TApplicationException) extends ThriftMessage(header){

  /**
    * Creates a new instance that contains a Thrift {@link TMessageType#REPLY} message.
    */
  def this(header: TMessage, result: TBase[_,_]) = this(header,result,null)

  /**
    * Creates a new instance that contains a Thrift {@link TMessageType#EXCEPTION} message.
    */
  def this(header: TMessage, exception: TApplicationException) = this(header,null,exception)
  /**
    * Returns {@code true} if the type of this reply is {@link TMessageType#EXCEPTION}.
    */
  def isException: Boolean = exception != null

  /**
    * Returns the result of this reply.
    *
    * @throws IllegalStateException if the type of this reply is not { @link TMessageType#REPLY}
    */
  def getResult: TBase[_,_] = {
    if (isException) throw new IllegalStateException("not a reply but an exception")
    result
  }

  /**
    * Returns the exception of this reply.
    *
    * @throws IllegalStateException if the type of this reply is not { @link TMessageType#EXCEPTION}
    */
  def getException: TApplicationException = {
    if (!isException) throw new IllegalStateException("not an exception but a reply")
    exception
  }

  override def equals(o: Any): Boolean = {
    if (this == o) return true
    if (o == null || (getClass ne o.getClass)) return false
    val that = o.asInstanceOf[ThriftReply]
    super.equals(that) && Objects.equals(result, that.result) && Objects.equals(exception, that.exception)
  }

  override def hashCode: Int = (super.hashCode * 31 + Objects.hashCode(result)) * 31 + Objects.hashCode(exception)

  override def toString: String = {
    val helper = MoreObjects.toStringHelper(this).add("seqId", header.seqid).add("type", typeStr).add("name", header.name)
    if (result != null) helper.add("result", result)
    else helper.add("exception", exception)
    helper.toString
  }
}