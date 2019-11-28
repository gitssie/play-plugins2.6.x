package play.api.config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import org.apache.commons.lang3.StringUtils
import play.api.Configuration
import play.libs.Json

object Conf {

  private [this] def getMapT[T](conf:play.Configuration,key:String,n:String,d:T,a:JsonNode => T):T = {
    val node = getJson(conf,key)
    if(node == null){
      return d
    }
    val ni = node.get(n)
    if(ni == null){
      return d
    }else{
      return a(ni)
    }
  }

  private [this] def getMapL[T](conf:Configuration,key:String,n:String,d:T,a:JsonNode => T):T = {
    val node = getJson(conf,key)
    if(node == null){
      return d
    }
    val ni = node.get(n)
    if(ni == null){
      return d
    }else{
      return a(ni)
    }
  }

  private [this] def getListT[T](conf:play.Configuration,key:String,n:Int,d:T,a:JsonNode => T):T = {
    val node = getJson(conf,key)
    if(node == null){
      return d
    }
    val ni = getNodeValueById(node,n)
    if(ni == null){
      return d
    }else{
      return a(ni)
    }
  }

  private [this] def getNodeValueById(node:JsonNode,id:Int):JsonNode = {
    val it = node.iterator()
    while(it.hasNext){
      val n = it.next()
      if(n.has("id") && n.has("name")){
         if(StringUtils.equals(n.get("id").asText(),id.toString)){
           return n.get("name")
         }
      }
    }
    return null
  }

  private [this] def getListL[T](conf:Configuration,key:String,n:Int,d:T,a:JsonNode => T):T = {
    val node = getJson(conf,key)
    if(node == null){
      return d
    }
    val ni = getNodeValueById(node,n)
    if(ni == null){
      return d
    }else{
      return a(ni)
    }
  }

  def getJson(conf:Configuration,key:String):JsonNode = conf.getOptional[String](key).map(Json.parse(_)).getOrElse(null)

  def getJson(conf:play.Configuration,key:String):JsonNode = {
    val s = conf.getString(key)
    if(StringUtils.isBlank(s)){
      return null
    }else{
      return Json.parse(s)
    }
  }

  def getMap(conf:Configuration,key:String):java.util.Map[String,Object] = conf.getOptional[String](key).map(n => Json.fromJson(Json.parse(n),classOf[java.util.Map[String,Object]])).getOrElse(null)

  def getMap(conf:play.Configuration,key:String):java.util.Map[String,Object] = {
    val n = getJson(conf,key)
    if(n != null){
      return Json.fromJson(n,classOf[java.util.Map[String,Object]])
    }else{
      return null
    }
  }

  /***------get config map by key--------------------**/

  def getMapInt(conf:play.Configuration,key:String,n:String,d:Int):Int = getMapT[Int](conf,key,n,d,node => node.asInt(d))
  def getMapInt(conf:play.Configuration,key:String,n:String):Int = getMapInt(conf,key,n,0)

  def getMapLong(conf:play.Configuration,key:String,n:String,d:Long):Long = getMapT[Long](conf,key,n,d,node => node.asLong(d))
  def getMapLong(conf:play.Configuration,key:String,n:String):Long = getMapLong(conf,key,n,0)

  def getMapDouble(conf:play.Configuration,key:String,n:String,d:Double):Double = getMapT[Double](conf,key,n,d,node => node.asDouble(d))
  def getMapDouble(conf:play.Configuration,key:String,n:String):Double = getMapDouble(conf,key,n,0)

  def getMapBoolean(conf:play.Configuration,key:String,n:String,d:Boolean):Boolean = getMapT[Boolean](conf,key,n,d,node => node.asBoolean(d))
  def getMapBoolean(conf:play.Configuration,key:String,n:String):Boolean = getMapBoolean(conf,key,n,false)

  def getMapString(conf:play.Configuration,key:String,n:String,d:String):String = getMapT[String](conf,key,n,d,node => node.asText(d))
  def getMapString(conf:play.Configuration,key:String,n:String):String = getMapString(conf,key,n,null)


  def getMapInt(conf:Configuration,key:String,n:String,d:Int):Int = getMapL[Int](conf,key,n,d,node => node.asInt(d))
  def getMapInt(conf:Configuration,key:String,n:String):Int = getMapInt(conf,key,n,0)

  def getMapLong(conf:Configuration,key:String,n:String,d:Long):Long = getMapL[Long](conf,key,n,d,node => node.asLong(d))
  def getMapLong(conf:Configuration,key:String,n:String):Long = getMapLong(conf,key,n,0)

  def getMapDouble(conf:Configuration,key:String,n:String,d:Double):Double = getMapL[Double](conf,key,n,d,node => node.asDouble(d))
  def getMapDouble(conf:Configuration,key:String,n:String):Double = getMapDouble(conf,key,n,0)

  def getMapBoolean(conf:Configuration,key:String,n:String,d:Boolean):Boolean = getMapL[Boolean](conf,key,n,d,node => node.asBoolean(d))
  def getMapBoolean(conf:Configuration,key:String,n:String):Boolean = getMapBoolean(conf,key,n,false)

  def getMapString(conf:Configuration,key:String,n:String,d:String):String = getMapL[String](conf,key,n,d,node => node.asText(d))
  def getMapString(conf:Configuration,key:String,n:String):String = getMapString(conf,key,n,null)

  /***------get config list by key--------------------**/

  def getListInt(conf:play.Configuration,key:String,n:Int,d:Int):Int = getListT[Int](conf,key,n,d,node => node.asInt(d))
  def getListInt(conf:play.Configuration,key:String,n:Int):Int = getListInt(conf,key,n,0)

  def getListLong(conf:play.Configuration,key:String,n:Int,d:Long):Long = getListT[Long](conf,key,n,d,node => node.asLong(d))
  def getListLong(conf:play.Configuration,key:String,n:Int):Long = getListLong(conf,key,n,0)

  def getListDouble(conf:play.Configuration,key:String,n:Int,d:Double):Double = getListT[Double](conf,key,n,d,node => node.asDouble(d))
  def getListDouble(conf:play.Configuration,key:String,n:Int):Double = getListDouble(conf,key,n,0)

  def getListBoolean(conf:play.Configuration,key:String,n:Int,d:Boolean):Boolean = getListT[Boolean](conf,key,n,d,node => node.asBoolean(d))
  def getListBoolean(conf:play.Configuration,key:String,n:Int):Boolean = getListBoolean(conf,key,n,false)

  def getListString(conf:play.Configuration,key:String,n:Int,d:String):String = getListT[String](conf,key,n,d,node => node.asText(d))
  def getListString(conf:play.Configuration,key:String,n:Int):String = getListString(conf,key,n,null)


  def getListInt(conf:Configuration,key:String,n:Int,d:Int):Int = getListL[Int](conf,key,n,d,node => node.asInt(d))
  def getListInt(conf:Configuration,key:String,n:Int):Int = getListInt(conf,key,n,0)

  def getListLong(conf:Configuration,key:String,n:Int,d:Long):Long = getListL[Long](conf,key,n,d,node => node.asLong(d))
  def getListLong(conf:Configuration,key:String,n:Int):Long = getListLong(conf,key,n,0)

  def getListDouble(conf:Configuration,key:String,n:Int,d:Double):Double = getListL[Double](conf,key,n,d,node => node.asDouble(d))
  def getListDouble(conf:Configuration,key:String,n:Int):Double = getListDouble(conf,key,n,0)

  def getListBoolean(conf:Configuration,key:String,n:Int,d:Boolean):Boolean = getListL[Boolean](conf,key,n,d,node => node.asBoolean(d))
  def getListBoolean(conf:Configuration,key:String,n:Int):Boolean = getListBoolean(conf,key,n,false)

  def getListString(conf:Configuration,key:String,n:Int,d:String):String = getListL[String](conf,key,n,d,node => node.asText(d))
  def getListString(conf:Configuration,key:String,n:Int):String = getListString(conf,key,n,null)
}
