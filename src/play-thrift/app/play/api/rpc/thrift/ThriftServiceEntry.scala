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

/**
  * The information about a Thrift service served by {@link THttpService} or {@link ThriftCallService}.
  *
  * @see THttpService#entries()
  * @see ThriftCallService#entries()
  */
class ThriftServiceEntry (val name:String,val implementation: AnyRef) {

  val metadata = new ThriftServiceMetadata(implementation)

  def interfaces: Set[Class[_]] = metadata.getInterfaces

  override def toString: String = MoreObjects.toStringHelper("Entry").add("name", name).add("ifaces", interfaces).add("impl", implementation).toString
}