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

import java.lang.reflect.{InvocationHandler, Method, UndeclaredThrowableException}

import org.apache.thrift.async.AsyncMethodCallback

import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

import scala.concurrent.ExecutionContext.Implicits.global

class THttpClientInvocationHandler (val thriftClient: THttpClient, val path: String, val serviceName: String) extends InvocationHandler {
  private val NO_ARGS = new Array[AnyRef](0)

  @throws[Throwable]
  override def invoke(proxy: AnyRef, method: Method, args: Array[AnyRef]): AnyRef = {
    val declaringClass = method.getDeclaringClass
    if (declaringClass eq classOf[Any]) { // Handle the methods in Object
      return invokeObjectMethod(proxy, method, args).asInstanceOf[AnyRef]
    }
    //assert declaringClass == declaringClass;
    // Handle the methods in the interface.
    invokeClientMethod(method, args).asInstanceOf[AnyRef]
  }

  private def invokeObjectMethod(proxy: AnyRef, method: Method, args: Array[AnyRef]):Any = {
    val methodName = method.getName
    methodName match {
      case "toString" =>
        method.getDeclaringClass.getSimpleName + '(' + path + ')'
      case "hashCode" =>
        System.identityHashCode(proxy)
      case "equals" =>
        proxy == args(0)
      case _ =>
        throw new Error("unknown method: " + methodName)
    }
  }

  @throws[Throwable]
  private def invokeClientMethod(method: Method, arg: Array[AnyRef]):Any = {
    var args = arg
    var callback:AsyncMethodCallback[Any] = null
    if (args == null) {
      args = NO_ARGS
    } else {
      val lastIdx = args.length - 1
      if (args.length > 0 && args(lastIdx).isInstanceOf[AsyncMethodCallback[_]]) {
        val lastArg = args(lastIdx).asInstanceOf[AsyncMethodCallback[Any]]
        callback = lastArg
        args = args.slice(0,lastIdx)
      }
    }
    try {
      var reply:Future[Any] = null
      if (serviceName != null) {
        reply = thriftClient.executeMultiplexed(path, method.getDeclaringClass, serviceName, method.getName, args)
      }else {
        reply = thriftClient.execute(path, method.getDeclaringClass, method.getName, args)
      }
      if (callback != null) {
        reply.onComplete({
          case Failure(t) => invokeOnError(callback,t)
          case Success(r) => callback.onComplete(r)
        })
        null
      }else {
        Await.result(reply,thriftClient.getDefaultDuration)
      }
    } catch {
      case cause: Throwable =>
        if (callback != null) {
          invokeOnError(callback, cause)
          null
        }else throw cause
    }
  }

  def invokeOnError(callback: AsyncMethodCallback[_], cause: Throwable): Unit = callback.onError(if (cause.isInstanceOf[Exception]) cause.asInstanceOf[Exception] else new UndeclaredThrowableException(cause))
}