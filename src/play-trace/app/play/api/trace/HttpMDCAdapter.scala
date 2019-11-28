package play.api.trace

import java.util
import java.util.{Collections, HashMap, Map}

import org.slf4j.spi.MDCAdapter
import play.mvc.Http

class HttpMDCAdapter(val delegate: MDCAdapter) extends MDCAdapter{
  val key:String = classOf[HttpMDCAdapter].getName

  def args: util.Map[String, String] = if(Http.Context.current.get() != null){
    var r = Http.Context.current.get().args.get(key).asInstanceOf[util.Map[String,String]]
    if(r == null){
      r = Collections.synchronizedMap(new util.HashMap[String, String])
      Http.Context.current.get().args.put(key,r)
    }
    r
  }else{
    null
  }

  override def getCopyOfContextMap = if(args == null) delegate.getCopyOfContextMap else args

  override def clear() = if(args == null) delegate.clear() else args.clear()

  override def remove(key: String) = if(args == null) delegate.remove(key) else args.remove(key)

  override def put(key: String, v: String) = if(args == null) delegate.put(key,v) else args.put(key,v)

  override def get(key: String) = if(args == null) delegate.get(key) else args.get(key)

  override def setContextMap(contextMap: util.Map[String, String]) = if(args == null) delegate.setContextMap(contextMap) else args.putAll(contextMap)
}
