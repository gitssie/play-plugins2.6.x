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

import java.nio.ByteBuffer
import org.apache.thrift.TBase
import org.apache.thrift.TFieldIdEnum

/**
  * Provides access to a Thrift field.
  */
object ThriftFieldAccess {
  /**
    * Gets the value of the specified struct field.
    */
    def get(struct: TBase[_,_], field: TFieldIdEnum): Any = {
      val value = struct.asInstanceOf[TBase[_,TFieldIdEnum]].getFieldValue(field)
      if (value.isInstanceOf[Array[Byte]])
        ByteBuffer.wrap(value.asInstanceOf[Array[Byte]])
      else value
    }

  /**
    * Sets the value of the specified struct field.
    */
  def set(struct: TBase[_,_], field: TFieldIdEnum, value: Any): Unit = struct.asInstanceOf[TBase[_,TFieldIdEnum]].setFieldValue(field, value)

  /**
    * Tells whether the specified struct field is set or not.
    */
  def isSet(struct: TBase[_,_], field: TFieldIdEnum): Boolean = struct.asInstanceOf[TBase[_,TFieldIdEnum]].isSet(field)
}
