package play.api.rpc.thrift

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import com.google.common.cache.{CacheBuilder, CacheLoader}
import org.apache.thrift.{TApplicationException, TBase, TException}
import org.apache.thrift.protocol.{TMessage, TMessageType, TProtocol}
import org.apache.thrift.transport.{TMemoryBuffer, TMemoryInputTransport}
import play.api.rpc.thrift.ThriftProtocolFactories.Format
import play.libs.transport.http.handler.BytesBodyHandler
import play.libs.transport.thrift.ThriftClient

import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

import scala.concurrent.ExecutionContext.Implicits.global

class THttpClientDelegate(val httpClient:ThriftClient,val format:Format.Type) extends THttpClient {
  private val nextSeqId = new AtomicInteger
  private val metadataMap = CacheBuilder.newBuilder().build[Class[_],ThriftServiceMetadata](new CacheLoader[Class[_],ThriftServiceMetadata] {
    override def load(k: Class[_]) = new ThriftServiceMetadata(k)
  })
  /**
    * Executes the specified Thrift call.
    *
    * @param path        the path of the Thrift service
    * @param serviceType the Thrift service interface
    * @param method      the method name
    * @param args        the arguments of the call
    */
  override def execute(path: String, serviceType: Class[_], method: String, args: Any*):Future[Any] = executeMultiplexed(path,serviceType,null,method,args)

  def fullName(serviceName: String, method: String): String = Option(serviceName).map(_ + ":" + method).getOrElse(method)

  /**
    * Executes the specified multiplexed Thrift call.
    *
    * @param path        the path of the Thrift service
    * @param serviceType the Thrift service interface
    * @param serviceName the Thrift service name
    * @param method      the method name
    * @param args        the arguments of the call
    */
  override def executeMultiplexed(path: String, serviceType: Class[_], serviceName: String, method: String, args: Any*):Future[Any] = {
    val reply = Promise[Any]()
    val seqId = nextSeqId.incrementAndGet
    Try(metadataMap.get(serviceType).getFunction(method)) match {
      case Failure(t) => reply.failure(t)
      case Success(r) if r.isEmpty => reply.failure(new IllegalArgumentException("Thrift method not found: " + method))
      case Success(r) => {
        val func = r.get
        try {
          val transport = new TMemoryBuffer(128)
          val tProtocol:TProtocol = ThriftProtocolFactories.createTProtocol(format,transport)
          val header = new TMessage(fullName(serviceName,method), func.messageType, seqId)
          tProtocol.writeMessageBegin(header)
          val tArgs = func.newArgs(args.toArray)
          tArgs.write(tProtocol)
          tProtocol.writeMessageEnd
          val reqBody = transport.getArray

          httpClient.execute(path,reqBody,new BytesBodyHandler()).future.onComplete{
            case Failure(t) => reply.failure(t)
            case Success(r) => handle(seqId,reply,func,r)
          }
        } catch {
          case e: Throwable => reply.failure(e)
        } finally {
          //if (!success) buf.release
        }
      }
    }
    reply.future
  }

  @throws[TException]
  private def handle(seqId: Int, reply: Promise[Any], func: ThriftFunction, content: Array[Byte]): Unit = {
    if (func.isOneWay) {
      handleSuccess(reply, null)
      return
    }
    if (content.isEmpty) throw new TApplicationException(TApplicationException.MISSING_RESULT)
    val inputTransport = new TMemoryInputTransport(content.array,0, content.length)
    val inputProtocol = ThriftProtocolFactories.createTProtocol(format,inputTransport)
    val header = inputProtocol.readMessageBegin
    val appEx = readApplicationException(seqId, func, inputProtocol, header)
    if (appEx != null) {
      handleException(reply, appEx)
      return
    }
    val result = func.newResult
    result.read(inputProtocol)
    inputProtocol.readMessageEnd()

    for ((_,fieldIdEnum) <- func.exceptionFields) {
      if (ThriftFieldAccess.isSet(result, fieldIdEnum)) {
        val cause = ThriftFieldAccess.get(result, fieldIdEnum).asInstanceOf[TException]
        handleException(reply, cause)
        return
      }
    }
    val successField = func.successField
    if (successField == null) { // void method
      handleSuccess(reply, null)
      return
    }
    if (ThriftFieldAccess.isSet(result, successField)) {
      val returnValue = ThriftFieldAccess.get(result, successField)
      handleSuccess(reply, returnValue)
      return
    }
    handleException(reply, new TApplicationException(TApplicationException.MISSING_RESULT, result.getClass.getName + '.' + successField.getFieldName))
  }

  def handleSuccess(reply: Promise[Any], returnValue: Any): Unit = {
    reply.success(returnValue)
  }

  def handleException(reply: Promise[Any], cause: Exception): Unit = {
    reply.failure(cause)
  }

  @throws[TException]
  private def readApplicationException(seqId: Int, func: ThriftFunction, inputProtocol: TProtocol, msg: TMessage): TApplicationException = {
    if (msg.seqid != seqId) throw new TApplicationException(TApplicationException.BAD_SEQUENCE_ID)
    if (!(func.name == msg.name)) return new TApplicationException(TApplicationException.WRONG_METHOD_NAME, msg.name)
    if (msg.`type` == TMessageType.EXCEPTION) {
      val appEx = ThriftHelper.readApplicationException(inputProtocol)
      inputProtocol.readMessageEnd()
      return appEx
    }
    null
  }

  override def getDefaultDuration = Duration.apply(60,TimeUnit.SECONDS)
}
