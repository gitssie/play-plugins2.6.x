
package play.api.rpc.thrift

import java.util.Objects.requireNonNull

import com.google.common.collect.ImmutableMap
import org.apache.thrift.{AsyncProcessFunction, ProcessFunction, TApplicationException, TBase, TBaseAsyncProcessor, TBaseProcessor, TException, TFieldIdEnum}
import org.apache.thrift.async.AsyncMethodCallback
import org.apache.thrift.meta_data.FieldMetaData
import org.apache.thrift.protocol.TMessageType
import org.apache.thrift.TApplicationException
import org.apache.thrift.TException
import org.apache.thrift.protocol.TField
import org.apache.thrift.protocol.TProtocol
import org.apache.thrift.protocol.TProtocolUtil
import org.apache.thrift.protocol.TType
import org.slf4j.LoggerFactory
import play.api.rpc.thrift.ThriftHelper.{invokeAsynchronously, invokeSynchronously}

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.concurrent.{Future, Promise}


object CallType extends Enumeration {
  type Type = Value
  val SYNC, ASYNC = Value
}

object ThriftHelper {
  private val logger = LoggerFactory.getLogger(getClass)

  def getResult(func: ProcessFunction[_, _]):TBase[_, _] = getResult0(CallType.SYNC, func.getClass, func.getMethodName)

  def getResult(asyncFunc: AsyncProcessFunction[_, _, _]):TBase[_, _] = getResult0(CallType.ASYNC, asyncFunc.getClass, asyncFunc.getMethodName)

  def getResult0(`type`: CallType.Type, funcClass: Class[_], methodName: String):TBase[_, _] = {
    val resultTypeName = typeName(`type`, funcClass, methodName, methodName + "_result")
    try {
      val resultType = Class.forName(resultTypeName, false, funcClass.getClassLoader).asInstanceOf[Class[TBase[_, _]]]
      resultType.newInstance
    } catch {
      case _: ClassNotFoundException =>
        // Oneway function does not have a result type.
        null
      case e: Exception =>
        throw new IllegalStateException("cannot determine the result type of method: " + methodName, e)
    }
  }

  def getArgs(func: ProcessFunction[_, _]):TBase[_,_] = getArgs0(CallType.SYNC, func.getClass, func.getMethodName)

  def getArgs(asyncFunc: AsyncProcessFunction[_, _, _]):TBase[_,_] = getArgs0(CallType.ASYNC, asyncFunc.getClass, asyncFunc.getMethodName)

  def getArgs0(`type`: CallType.Type, funcClass: Class[_], methodName: String):TBase[_,_] = {
    val argsTypeName = typeName(`type`, funcClass, methodName, methodName + "_args")
    try {
      val argsType = Class.forName(argsTypeName, false, funcClass.getClassLoader).asInstanceOf[Class[TBase[_,_]]]
      argsType.newInstance
    } catch {
      case e: Exception =>
        throw new IllegalStateException("cannot determine the args class of method: " + methodName, e)
    }
  }

  def getArgFields(func: ProcessFunction[_,_]):Array[TFieldIdEnum] = getArgFields0(CallType.SYNC, func.getClass, func.getMethodName)

  def getArgFields(asyncFunc: AsyncProcessFunction[_,_,_]):Array[TFieldIdEnum] = getArgFields0(CallType.ASYNC, asyncFunc.getClass, asyncFunc.getMethodName)

  def getArgFields0(`type`: CallType.Type, funcClass: Class[_], methodName: String):Array[TFieldIdEnum] = {
    val fieldIdEnumTypeName = typeName(`type`, funcClass, methodName, methodName + "_args$_Fields")
    try {
      val fieldIdEnumType = Class.forName(fieldIdEnumTypeName, false, funcClass.getClassLoader)
      requireNonNull(fieldIdEnumType.getEnumConstants, "field enum may not be empty").asInstanceOf[Array[TFieldIdEnum]]
    } catch {
      case e: Exception =>
        throw new IllegalStateException("cannot determine the arg fields of method: " + methodName, e)
    }
  }

  def getDeclaredExceptions(func: ProcessFunction[_, _]): Array[Class[_]]  = getDeclaredExceptions0(CallType.SYNC, func.getClass, func.getMethodName)

  def getDeclaredExceptions(asyncFunc: AsyncProcessFunction[_, _, _]): Array[Class[_]]  = getDeclaredExceptions0(CallType.ASYNC, asyncFunc.getClass, asyncFunc.getMethodName)

  def getDeclaredExceptions0(`type`: CallType.Type, funcClass: Class[_], methodName: String): Array[Class[_]] = {
    val ifaceTypeName = typeName(`type`, funcClass, methodName, "Iface")
    try {
      val ifaceType = Class.forName(ifaceTypeName, false, funcClass.getClassLoader)
      for (m <- ifaceType.getDeclaredMethods) {
        if (m.getName == methodName){
          return m.getExceptionTypes
        }
      }
      throw new IllegalStateException("failed to find a method: " + methodName)
    } catch {
      case e: Exception =>
        throw new IllegalStateException("cannot determine the declared exceptions of method: " + methodName, e)
    }
  }

  def typeName(`type`: CallType.Type, funcClass: Class[_], methodName: String, toAppend: String):String = {
    val funcClassName = funcClass.getName
    val serviceClassEndPos = funcClassName.lastIndexOf((if (`type` eq CallType.SYNC) "$Processor$"
    else "$AsyncProcessor$") + methodName)
    if (serviceClassEndPos <= 0) throw new IllegalStateException("cannot determine the service class of method: " + methodName)
    funcClassName.substring(0, serviceClassEndPos) + '$' + toAppend
  }

  def getSuccessField(result:TBase[_,_]):TFieldIdEnum = {
    var successField:TFieldIdEnum = null
    if (result != null) { // if not oneway
      val resultType= result.getClass
      val metaDataMap = FieldMetaData.getStructMetaDataMap(resultType.asInstanceOf[Nothing])
      for (e <- metaDataMap.entrySet().asScala) {
        val key = e.getKey
        val fieldName = key.getFieldName
        if ("success" == fieldName) {
          successField = key
        }
      }
    }
    successField
  }

  def getExceptionFields(result:TBase[_,_]):Map[Class[Throwable], TFieldIdEnum] = {
    val exceptionFieldsBuilder = ImmutableMap.builder[Class[Throwable], TFieldIdEnum]
    if (result != null) { // if not oneway
      val resultType = result.getClass
      val metaDataMap = FieldMetaData.getStructMetaDataMap(resultType.asInstanceOf[Nothing])
      for (e <- metaDataMap.entrySet().asScala) {
        val key = e.getKey
        val fieldName = key.getFieldName
        val fieldType = resultType.getField(fieldName).getType
        if (classOf[Throwable].isAssignableFrom(fieldType)) {
          val exceptionFieldType = fieldType.asInstanceOf[Class[Throwable]]
          exceptionFieldsBuilder.put(exceptionFieldType, key)
        }
      }
    }
    exceptionFieldsBuilder.build().asScala.toMap
  }

  def getThriftProcessMap(service: AnyRef, iface: Class[_]): Map[String, ProcessFunction[_,_]] = {
    val name = iface.getName
    if (!name.endsWith("$Iface")) return null
    val processorName = name.substring(0, name.length - 5) + "Processor"
    try {
      val processorClass = Class.forName(processorName, false, iface.getClassLoader)
      if (!classOf[TBaseProcessor[_]].isAssignableFrom(processorClass)) return null
      val processorConstructor = processorClass.getConstructor(iface)
      val processor = processorConstructor.newInstance(service).asInstanceOf[TBaseProcessor[_]]
      val processMap = processor.getProcessMapView.asScala
      processMap.toMap
    } catch {
      case e: Exception =>
        logger.debug("Failed to retrieve the process map from:"+iface, e)
        null
    }
  }

  def getThriftAsyncProcessMap(service: AnyRef, iface: Class[_]): Map[String, AsyncProcessFunction[_,_,_]] = {
    val name = iface.getName
    if (!name.endsWith("$AsyncIface")) return null
    val processorName = name.substring(0, name.length - 10) + "AsyncProcessor"
    try {
      val processorClass = Class.forName(processorName, false, iface.getClassLoader)
      if (!classOf[TBaseAsyncProcessor[_]].isAssignableFrom(processorClass)) return null
      val processorConstructor = processorClass.getConstructor(iface)
      val processor = processorConstructor.newInstance(service).asInstanceOf[TBaseAsyncProcessor[_]]
      val processMap = processor.getProcessMapView.asScala
      processMap.toMap
    } catch {
      case e: Exception =>
        logger.debug("Failed to retrieve the asynchronous process map from:"+iface, e)
        null
    }
  }

  def checkDuplicateMethodName(methodNames: mutable.Set[String], name: String):Unit = if (methodNames.contains(name)) throw new IllegalArgumentException("duplicate Thrift method name: " + name)

  def getAllInterfaces(clazz:Class[_]):Set[Class[_]] = {
    val result = mutable.Set.empty
    var cls = clazz
    while(cls != null && cls != classOf[Object]){
      result ++ cls.getInterfaces
      cls = cls.getSuperclass
    }
    result.toSet
  }

  def invokeSynchronously(impl: AnyRef, func: ThriftFunction, args: TBase[_,_], reply: Promise[Any]): Unit = {
    val f = func.syncFunc
    try {
      val result = f.getResult(impl, args)
      if (func.isOneWay) reply.success(null)
      else reply.success(func.getResult(result))
    } catch {
      case t: Throwable =>
        reply.failure(t)
    }
  }

  def invokeAsynchronously(impl: AnyRef, func: ThriftFunction, args: TBase[_,_], reply: Promise[Any]): Unit = {
    val f = func.asyncFunc
    f.start(impl, args, new AsyncMethodCallback[AnyRef]() {
      override def onComplete(response: AnyRef): Unit = {
        if (func.isOneWay) reply.success(null)
        else reply.success(response)
      }
      override def onError(e: Exception): Unit = reply.failure(e)
    })
  }

  def typeString(b: Byte):String = b match {
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

  /**
    * Reads a {@link TApplicationException} from the specified {@link TProtocol}.
    *
    * <p>Note: This has been copied from {@link TApplicationException#read(TProtocol)} due to API differences
    * between libthrift 0.9.x and 0.10.x.
    */
  @throws[TException]
  def readApplicationException(iprot: TProtocol): TApplicationException = {
    var field:TField = null
    iprot.readStructBegin
    var message:String = null
    var t = TApplicationException.UNKNOWN
    var loop = true
    while (loop){
      field = iprot.readFieldBegin
      if(field.`type` == TType.STOP){
        loop = false
      }else {
        field.id match {
          case 1 => if(field.`type` == TType.STRING){
            message = iprot.readString()
          }else {
            TProtocolUtil.skip(iprot, field.`type`)
          }
          case 2 => if(field.`type` == TType.I32){
            t = iprot.readI32()
          }else {
            TProtocolUtil.skip(iprot, field.`type`)
          }
          case _ => TProtocolUtil.skip(iprot, field.`type`)
        }
        iprot.readFieldEnd()
      }
    }
    iprot.readStructEnd()
    new TApplicationException(t, message)
  }
}

class ThriftFunction(val serviceType:Class[_],
                     val name:String,
                     val func:AnyRef,
                     val callType:CallType.Type,
                     val result:TBase[_,_],
                     val argFields:Array[TFieldIdEnum],
                     val successField:TFieldIdEnum,
                     val exceptionFields:Map[Class[Throwable], TFieldIdEnum],
                     val declaredExceptions:Array[Class[_]]) {


  def this(serviceType:Class[_],
      name:String,
      func:AnyRef,
      callType:CallType.Type,
      result:TBase[_,_],
      argFields:Array[TFieldIdEnum],
      declaredExceptions:Array[Class[_]]) = this(serviceType,name,func,callType,result,argFields,ThriftHelper.getSuccessField(result),ThriftHelper.getExceptionFields(result),declaredExceptions)

  def this(serviceType:Class[_], func:ProcessFunction[_,_]) =  this(serviceType, func.getMethodName, func, CallType.SYNC,ThriftHelper.getResult(func),ThriftHelper.getArgFields(func),ThriftHelper.getDeclaredExceptions(func))

  def this(serviceType:Class[_], func:AsyncProcessFunction[_,_,_]) =  this(serviceType, func.getMethodName, func, CallType.ASYNC,ThriftHelper.getResult(func),ThriftHelper.getArgFields(func),ThriftHelper.getDeclaredExceptions(func))

  /**
    * Returns {@code true} if this function is a one-way.
    */
  def isOneWay: Boolean = result == null

  /**
    * Returns {@code true} if this function is asynchronous.
    */
  def isAsync: Boolean = callType == CallType.ASYNC

  /**
    * Returns the type of this function.
    *
    * @return { @link TMessageType#CALL} or { @link TMessageType#ONEWAY}
    */
  def messageType: Byte = if (isOneWay) TMessageType.ONEWAY else TMessageType.CALL

  /**
    * Returns the {@link ProcessFunction}.
    *
    * @throws ClassCastException if this function is asynchronous
    */
  def syncFunc: ProcessFunction[AnyRef, TBase[_,_]] = func.asInstanceOf[ProcessFunction[AnyRef, TBase[_,_]]]

  /**
    * Returns the {@link AsyncProcessFunction}.
    *
    * @throws ClassCastException if this function is synchronous
    */
  def asyncFunc: AsyncProcessFunction[AnyRef, TBase[_,_], AnyRef] = func.asInstanceOf[AsyncProcessFunction[AnyRef, TBase[_,_], AnyRef]]

  /**
    * Returns a new empty arguments instance.
    */
  def newArgs: TBase[_,_] = if (isAsync) asyncFunc.getEmptyArgsInstance else syncFunc.getEmptyArgsInstance

  /**
    * Returns a new arguments instance.
    */
  def newArgs(args: Array[Any]): TBase[_,_] = {
    val newArgs: TBase[_,_] = this.newArgs
    for(i <- args.indices){
      ThriftFieldAccess.set(newArgs, argFields(i), args(i))
    }
    newArgs
  }

  /**
    * Returns a new empty result instance.
    */
  def newResult: TBase[_,_] = result.deepCopy().asInstanceOf[TBase[_,_]]

  /**
    * Sets the success field of the specified {@code result} to the specified {@code value}.
    */
  def setSuccess(result: TBase[_,_], value: Any): Unit = {
    if (successField != null) ThriftFieldAccess.set(result, successField, value)
  }

  /**
    * Converts the specified {@code result} into a Java object.
    */
  @throws[TException]
  def getResult(result: TBase[_,_]): Any = {
    for (fieldIdEnum <- exceptionFields.values) {
      if (ThriftFieldAccess.isSet(result, fieldIdEnum))
        throw ThriftFieldAccess.get(result, fieldIdEnum).asInstanceOf[TException]
    }
    if (successField == null) { //void method
      null
    }else if (ThriftFieldAccess.isSet(result, successField)) ThriftFieldAccess.get(result, successField)
    else throw new TApplicationException(TApplicationException.MISSING_RESULT, result.getClass.getName + '.' + successField.getFieldName)
  }

  /**
    * Sets the exception field of the specified {@code result} to the specified {@code cause}.
    */
  def setException(result: TBase[_,_], cause: Throwable): Boolean = {
    val causeType = cause.getClass
    for (e <- exceptionFields) {
      if (e._1.isAssignableFrom(causeType)) {
        ThriftFieldAccess.set(result, e._2, cause)
        return true
      }
    }
    false
  }

  def invoke(impl: AnyRef,tArgs: TBase[_,_]): Future[Any] = {
    val func = this
    val reply = Promise[Any]()
    try {
      if (func.isAsync){
        invokeAsynchronously(impl, func, tArgs, reply)
      }else{
        invokeSynchronously(impl, func, tArgs, reply)
      }
    } catch {
      case t: Throwable =>
        reply.failure(t)
    }
    reply.future
  }
}

class ThriftServiceMetadata {
  private var interfaces:Set[Class[_]] = null
  /**
    * A map whose key is a method name and whose value is {@link AsyncProcessFunction} or
    * {@link ProcessFunction}.
    */
  private val functions = mutable.Map.empty[String, ThriftFunction]

  /**
    * Creates a new instance from a Thrift service implementation that implements one or more Thrift service
    * interfaces.
    */
  def this(implementation: AnyRef) {
    this()
    requireNonNull(implementation, "implementation")
    interfaces = init(implementation)
  }

  /**
    * Creates a new instance from a single Thrift service interface.
    */
  def this(serviceType: Class[_]) {
    this()
    requireNonNull(serviceType, "serviceType")
    interfaces = init(null, Set(serviceType))
  }

  private def init(implementation: AnyRef):Set[Class[_]] = init(implementation,ThriftHelper.getAllInterfaces(implementation.getClass))

  private def init(implementation: AnyRef, candidateInterfaces: Set[Class[_]]):Set[Class[_]] = { // Build the map of method names and their corresponding process functions.
    val methodNames = mutable.Set.empty[String]
    val interfaces = mutable.Set.empty[Class[_]]
    for (iface <- candidateInterfaces) {
      val asyncProcessMap = ThriftHelper.getThriftAsyncProcessMap(implementation, iface)
      if (asyncProcessMap != null) {
        asyncProcessMap.foreach{
          case (name,func) => registerFunction(methodNames, iface, name, func)
        }
        interfaces.add(iface)
      }
      val processMap = ThriftHelper.getThriftProcessMap(implementation, iface)
      if (processMap != null) {
        processMap.foreach{
          case (name,func) => registerFunction(methodNames, iface, name, func)
        }
        interfaces.add(iface)
      }
    }
    interfaces.toSet
  }

  private def registerFunction(methodNames: mutable.Set[String], iface: Class[_], name: String, func: Any) = {
    ThriftHelper.checkDuplicateMethodName(methodNames, name)
    methodNames ++ name
    try {
      var f:ThriftFunction = null
      if (func.isInstanceOf[ProcessFunction[_, _ <: TBase[_, _ <: TFieldIdEnum]]]) f = new ThriftFunction(iface, func.asInstanceOf[ProcessFunction[_,_]])
      else f = new ThriftFunction(iface, func.asInstanceOf[AsyncProcessFunction[_, _ ,_]])
      functions.put(name, f)
    } catch {
      case e: Exception =>
        throw new IllegalArgumentException("failed to retrieve function metadata: " + iface.getName + '.' + name + "()", e)
    }
  }

  /**
    * Returns the Thrift service interfaces implemented.
    */
  def getInterfaces: Set[Class[_]] = interfaces

  /**
    * Returns the {@link ThriftFunction} that provides the metadata of the specified Thrift function.
    *
    * @return the { @link ThriftFunction}. { @code null} if there's no such function.
    */
  def getFunction(method: String): Option[ThriftFunction] = functions.get(method)


}














