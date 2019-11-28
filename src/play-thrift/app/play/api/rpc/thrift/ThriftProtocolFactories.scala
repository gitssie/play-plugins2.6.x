/*
 * Copyright 2015 LINE Corporation
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
 * Copyright 2015 LINE Corporation
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

import java.util.Objects.requireNonNull

import org.apache.thrift.protocol._
import org.apache.thrift.transport.TTransport
import play.api.http.MediaType
import play.api.rpc.thrift.ThriftProtocolFactories.Format
import play.api.rpc.thrift.ThriftProtocolFactories.Format.Type

/**
  * Provides a set of the known {@link TProtocolFactory} instances.
  */
object ThriftProtocolFactories {

  object Format extends Enumeration {
    type Type = Value
    val TBINARY, TCOMPACT,TJSON,TTEXT = Value
  }

  /**
    * {@link TProtocolFactory} for Thrift TBinary protocol.
    */
    val BINARY: TProtocolFactory = new TBinaryProtocol.Factory() {
      override def toString = "TProtocolFactory(tbinary)"
    }
  /**
    * {@link TProtocolFactory} for Thrift TCompact protocol.
    */
  val COMPACT: TProtocolFactory = new TCompactProtocol.Factory() {
    override def toString = "TProtocolFactory(tcompact)"
  }
  /**
    * {@link TProtocolFactory} for the Thrift TJSON protocol.
    */
  val JSON: TProtocolFactory = new TJSONProtocol.Factory() {
    override def toString = "TProtocolFactory(TJSON)"
  }
  /**
    * {@link TProtocolFactory} for the Thrift TText protocol.
    */
  val TEXT: TProtocolFactory = new TJSONProtocol.Factory() {
    override def toString = "TProtocolFactory(TTEXT)"
  }

  /**
    * Returns the {@link TProtocolFactory} for the specified {@link SerializationFormat}.
    *
    * @throws IllegalArgumentException if the specified { @link SerializationFormat} is not for Thrift
    */
  def get(serializationFormat: Format.Type): TProtocolFactory = {
    requireNonNull(serializationFormat, "serializationFormat")
    if (serializationFormat == Format.TBINARY) return BINARY
    if (serializationFormat == Format.TCOMPACT) return COMPACT
    if (serializationFormat == Format.TJSON) return JSON
    if (serializationFormat == Format.TTEXT) return TEXT
    throw new IllegalArgumentException("non-Thrift serializationFormat: " + serializationFormat)
  }

  /**
    * Returns the {@link SerializationFormat} for the specified {@link TProtocolFactory}.
    *
    * @throws IllegalArgumentException if the specified { @link TProtocolFactory} is not known by this class
    */
  def toSerializationFormat(protoFactory: TProtocolFactory): Format.Type = {
    requireNonNull(protoFactory, "protoFactory")
    if (protoFactory.isInstanceOf[TBinaryProtocol.Factory]) Format.TBINARY
    else if (protoFactory.isInstanceOf[TCompactProtocol.Factory]) Format.TCOMPACT
    else if (protoFactory.isInstanceOf[TJSONProtocol.Factory]) Format.TJSON
    //else if (protoFactory.isInstanceOf[Nothing]) Format.TTEXT
    else throw new IllegalArgumentException("unsupported TProtocolFactory: " + protoFactory.getClass.getName)
  }

  val allowedFormat:PartialFunction[MediaType,Option[Format.Type]] = {
    case m if ( m.mediaSubType == "x-thrift") => {
      m.parameters.find(_._1 == "protocol").flatMap(_._2).flatMap{
        case "TBINARY" => Some(Format.TBINARY)
        case "TCOMPACT" => Some(Format.TCOMPACT)
        case "TJSON" => Some(Format.TJSON)
        case "TTEXT" => Some(Format.TTEXT)
        case _ => None
      }
    }
    case m if (m.mediaSubType == "vnd.apache.thrift.binary") => Some(Format.TBINARY)
    case m if (m.mediaSubType == "vnd.apache.thrift.compact") => Some(Format.TCOMPACT)
    case m if (m.mediaSubType == "vnd.apache.thrift.json") => Some(Format.TJSON)
    case m if (m.mediaSubType == "vnd.apache.thrift.text") => Some(Format.TTEXT)
    case _ => None
  }

  val binaryMediaType:MediaType = new MediaType("application","vnd.apache.thrift.binary",Seq("charset" -> Some("utf-8")))

  def toSerializationFormat(contentType:Option[String]): (MediaType,Format.Type) = {
    val tp = contentType.flatMap(MediaType.parse(_)).find(_.mediaType == "application")
    val mtype = tp.getOrElse(binaryMediaType)
    val format = tp.flatMap(allowedFormat).getOrElse(Format.TBINARY)
    mtype -> format
  }

  def createTProtocol(formatType: Type, transport: TTransport): TProtocol = get(formatType).getProtocol(transport)


}