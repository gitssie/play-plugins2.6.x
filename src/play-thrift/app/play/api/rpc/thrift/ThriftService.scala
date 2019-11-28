package play.api.rpc.thrift

import akka.util.ByteString
import com.google.common.base.Throwables
import org.apache.thrift.protocol.{TMessage, TMessageType, TProtocol}
import org.apache.thrift.transport.TMemoryBuffer
import org.apache.thrift.{TApplicationException, TBase, TException}
import org.slf4j.LoggerFactory
import play.api.http.{HeaderNames, MediaType}
import play.api.mvc._
import play.api.rpc.thrift.ThriftProtocolFactories.Format

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/**
  * @Singleton
  * class TestThrift extends ThriftController{
  * serv("hello",iface)
  * }
  *
  *  POST /rpc/thrift   @xxx.xxx.xx.thrift
  *
  *
  * Iface face = ThriftClients.newClient(uri,promieClient).serv("serviceName",Iface)
  * face.hello("xxx");
  *
  *
  * application/x-thrift; protocol=TBINARY; charset=utf8;
  * application/vnd.apache.thrift.json; charset=utf8
  */
trait ThriftService {
  val logger = LoggerFactory.getLogger(classOf[ThriftService])
  val entries = mutable.Map.empty[String,ThriftServiceEntry]

  def apply(request: Request[RawBuffer]):Future[Result] = {
    val transport = request.body.asBytes().map(new TByteStringTransport((_)))
    if(transport.isEmpty){
      if(logger.isDebugEnabled()) logger.debug("Failed to decode Thrift header,request body is empty")
      return Future.successful(Results.BadRequest("Failed to decode Thrift header,request body is empty"))

    }
    val (mediaType,format) = ThriftProtocolFactories.toSerializationFormat(request.headers.get(HeaderNames.CONTENT_TYPE))
    val inProto:TProtocol = ThriftProtocolFactories.createTProtocol(format,transport.get)

    Try(inProto.readMessageBegin()) match {
      case Failure(t) => {
        if(logger.isDebugEnabled()) logger.debug("Failed to decode Thrift header:",t)
         return Future.successful(Results.BadRequest("Failed to decode Thrift header: " +Throwables.getStackTraceAsString(t)))
      }
      case Success(header) => {
        val seqId = header.seqid
        val typeValue = header.`type`
        val colonIdx = header.name.indexOf(':')
        var serviceName:String = null
        var methodName:String = null
        if (colonIdx < 0) {
          serviceName = ""
          methodName = header.name
        } else {
          serviceName = header.name.substring(0, colonIdx)
          methodName = header.name.substring(colonIdx + 1)
        }

        // Basic sanity check. We usually should never fail here.// Basic sanity check. We usually should never fail here.

        if ((typeValue != TMessageType.CALL) && (typeValue != TMessageType.ONEWAY)) {
          val cause = new TApplicationException(TApplicationException.INVALID_MESSAGE_TYPE, "unexpected TMessageType: " + ThriftHelper.typeString(typeValue))
          val result = handlePreDecodeException(methodName,seqId,format,cause)
          return Future.successful(toResult(result,mediaType))
        }

        // Ensure that such a method exists.// Ensure that such a method exists.

        val entry:Option[ThriftServiceEntry] = entries.get(serviceName)
        val func:Option[ThriftFunction] = entry.flatMap(_.metadata.getFunction(methodName))

        if (func.isEmpty) {
          val cause = new TApplicationException(TApplicationException.UNKNOWN_METHOD, "unknown method: " + header.name)
          val result = handlePreDecodeException(methodName,seqId,format,cause)
          return Future.successful(toResult(result,mediaType))
        }
        Try{
          val args = func.get.newArgs
          args.read(inProto)
          inProto.readMessageEnd
          args
        } match {
          case Failure(t) => {
            if(logger.isDebugEnabled()) logger.debug("Failed to decode Thrift arguments:",t)
            val cause = new TApplicationException(TApplicationException.PROTOCOL_ERROR, "failed to decode arguments: " + t)
            val result = handlePreDecodeException(methodName,seqId,format,cause)
            return Future.successful(toResult(result,mediaType))
          }
          case Success(args) => {
            implicit val ex = executionContext
            invoke(methodName,seqId,func.get,entry.get,args,format).recover{
              case t:Throwable => handleException(methodName,seqId,func.get,format,t)
            }.map(toResult(_,mediaType))
          }
        }
      }
    }
  }

  def toResult(bytes:ByteString,mediaType: MediaType):Result = Results.Ok(bytes).as(mediaType.toString())

  def handlePreDecodeException(name:String,seqId: Int,format:Format.Type,cause: Throwable): ByteString = encodeException(name,seqId,format,cause)

  def handleSuccess(seqId: Int, func: ThriftFunction, result: Any): TBase[_,_]= {
    val wrappedResult = func.newResult
    func.setSuccess(wrappedResult, result)
    wrappedResult
  }

  def encodeSuccess(name: String, seqId: Int,format:Format.Type,result: TBase[_,_]):ByteString = {
    //val buf:ByteBuffer = null;//ctx.alloc.buffer(128)
    var success = false
    try {
      val transport = new TMemoryBuffer(128)
      val outProto:TProtocol = ThriftProtocolFactories.createTProtocol(format,transport)
      val header = new TMessage(name, TMessageType.REPLY, seqId)
      outProto.writeMessageBegin(header)
      result.write(outProto)
      outProto.writeMessageEnd()

      val encoded = ByteString(transport.getArray)
      success = true
      encoded
    } catch {
      case e: TException =>
        throw new RuntimeException(e) // Should never reach here.

    } finally{
      //if (!success) buf.release
    }
  }

  def encodeException(name:String,seqId: Int,format: Format.Type,cause: Throwable) = {
    var appException:TApplicationException = null
    if (cause.isInstanceOf[TApplicationException]) {
      appException = cause.asInstanceOf[TApplicationException]
    }else {
      appException = new TApplicationException(TApplicationException.INTERNAL_ERROR, "internal server error:" + System.lineSeparator + "---- BEGIN server-side trace ----" + System.lineSeparator + Throwables.getStackTraceAsString(cause) + "---- END server-side trace ----")
    }
    var success = false
    try {
      val transport = new TMemoryBuffer(128)
      val outProto:TProtocol = ThriftProtocolFactories.createTProtocol(format,transport)
      val header = new TMessage(name, TMessageType.EXCEPTION, seqId)
      outProto.writeMessageBegin(header)
      appException.write(outProto)
      outProto.writeMessageEnd()
      val encoded = ByteString(transport.getArray)
      success = true
      encoded
    } catch {
      case e: TException =>
        throw new RuntimeException(e) // Should never reach here.

    } finally {
      //if (!success) buf.release
    }
  }

  def handleException(name:String,seqId: Int, func: ThriftFunction,format:Format.Type,cause: Throwable): ByteString = {
    val result = func.newResult
    if (func.setException(result, cause)){
      encodeSuccess(name,seqId,format,result)
    }else{
      encodeException(name,seqId,format,cause)
    }
  }

  def handleOneWaySuccess(f: ThriftFunction): ByteString = ByteString.empty

  def invoke(name:String,seqId: Int, f: ThriftFunction, entry: ThriftServiceEntry, args: TBase[_,_],format:Format.Type)(implicit ex: ExecutionContext): Future[ByteString] = {
    f.invoke(entry.implementation,args).map(result => {
      if(f.isOneWay){
        handleOneWaySuccess(f)
      }else {
        Try(handleSuccess(seqId, f, result)) match {
          case Success(r) => encodeSuccess(name, seqId, format, r)
          case Failure(t) => handleException(name, seqId, f, format, t)
        }
      }
    })
  }

  def executionContext:ExecutionContext

  def register(impRef:AnyRef):Unit = register("",impRef)

  def register(name:String,impRef: AnyRef):Unit = entries += (name -> new ThriftServiceEntry(name,impRef))
}
