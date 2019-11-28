/**
  * Licensed to the Apache Software Foundation (ASF) under one
  * or more contributor license agreements.  See the NOTICE file
  * distributed with this work for additional information
  * regarding copyright ownership.  The ASF licenses this file
  * to you under the Apache License, Version 2.0 (the
  * "License"); you may not use this file except in compliance
  * with the License.  You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing,
  * software distributed under the License is distributed on an
  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  * KIND, either express or implied.  See the License for the
  * specific language governing permissions and limitations
  * under the License.
  */
package play.api.ddsl

import java.util

import com.google.common.collect.Lists

class ServiceInstance(val name: String, val id: String, val address: String, val port: Int, val sslPort: Option[Int], val registrationTimeUTC: Long,val tags: util.List[String],val serviceType: ServiceType.ServiceType,val uriSpec:String,val enabled: Boolean){

  def this(name: String, id: String, address: String, port: Int, sslPort: Option[Int],registrationTimeUTC: Long,tags: util.List[String],serviceType: ServiceType.ServiceType) {
    this(name, id, address, port, sslPort, registrationTimeUTC, tags,serviceType, null,true)
  }

  def this(name: String, id: String, address: String, port: Int, sslPort: Option[Int],registrationTimeUTC: Long) {
    this(name, id, address, port, sslPort, registrationTimeUTC,Lists.newArrayList(),ServiceType.DYNAMIC,null, true)
  }

  def getName: String = name

  def getId: String = id

  def getAddress: String = address

  def getPort: Int = port

  def getSslPort: Option[Int] = sslPort

  def getRegistrationTimeUTC: Long = registrationTimeUTC

  def getServiceType: ServiceType.ServiceType = serviceType

  def isEnabled: Boolean = enabled

  def getTags:util.List[String] = tags

  def getUriSpec = uriSpec

  override def equals(o: Any): Boolean = {
    if (this == o) return true
    if (o == null || (getClass ne o.getClass)) return false
    val that = o.asInstanceOf[ServiceInstance]
    if (registrationTimeUTC != that.registrationTimeUTC) return false
    if (if (address != null) !(address == that.address)
    else that.address != null) return false
    if (if (id != null) !(id == that.id)
    else that.id != null) return false
    if (if (name != null) !(name == that.name)
    else that.name != null) return false
    if (if (port != null) !(port == that.port)
    else that.port != null) return false
    if (serviceType ne that.serviceType) return false
    if (if (sslPort != null) !(sslPort == that.sslPort)
    else that.sslPort != null) return false
    if (enabled != that.enabled) return false
    true
  }

  override def hashCode: Int = {
    var result = if (name != null) name.hashCode else 0
    result = 31 * result + (if (id != null) id.hashCode else 0)
    result = 31 * result + (if (address != null) address.hashCode else 0)
    result = 31 * result + (if (port != null) port.hashCode else 0)
    result = 31 * result + (if (sslPort != null) sslPort.hashCode else 0)
    result = 31 * result + (registrationTimeUTC ^ (registrationTimeUTC >>> 32)).toInt
    result = 31 * result + (if (serviceType != null) serviceType.hashCode else 0)
    result = 31 * result + (if (enabled) 1 else 0)
    result
  }

  override def toString: String = "ServiceInstance{" + "name='" + name + '\'' + ", id='" + id + '\'' + ", address='" + address + '\'' + ", port=" + port + ", sslPort=" + sslPort + ", registrationTimeUTC=" + registrationTimeUTC + ", serviceType=" + serviceType + ", enabled=" + enabled + '}'
}

object ServiceType extends Enumeration{
  type ServiceType = Value

  val DYNAMIC, STATIC, PERMANENT, DYNAMIC_SEQUENTIAL = Value

  def isDynamic: Boolean = (this eq DYNAMIC) || (this eq DYNAMIC_SEQUENTIAL)
}
