package play.api.config

import java.util
import java.util.Date

import com.google.common.collect.Lists
import com.typesafe.config.{Config, ConfigFactory}

trait ConfigProvider {

  def bind(onChange: (Option[String],Config) => Unit):Unit

  def get:Config

  def put(key:String,v:ConfValue):Unit

  def remove(key:String):Unit

  def get(key:String):ConfValue

  def list:util.Collection[ConfValue]

  def checkExists(key:String):Boolean
}

class EmptyConfigProvider extends ConfigProvider{

  override def bind(onChange: (Option[String],Config) => Unit): Unit = {}

  override def get: Config = ConfigFactory.empty()

  override def put(key: String, v: ConfValue): Unit = {}

  override def remove(key: String): Unit = {}

  override def get(key: String):ConfValue = null

  override def list:util.Collection[ConfValue] = Lists.newArrayList()

  override def checkExists(key: String) = false
}

/**
  * 配置对象
  */
class ConfValue(var key:String,var name:String,var cfType:String,var group:String,var value:String,var showView: Boolean,var addTime: Date){

  def this() = this(null,null,null,null,null,false,null)

  def getKey:String = key

  def setKey(key:String):Unit = this.key = key

  def getName:String = name

  def setName(name:String):Unit = this.name = name

  def getType:String = cfType

  def setType(cfType:String):Unit = this.cfType = cfType

  def getGroup:String = group

  def setGroup(group:String):Unit = this.group = group

  def getValue:String = value

  def setValue(value:String):Unit = this.value = value

  def isShow:Boolean = showView

  def setShow(showView:Boolean):Unit = this.showView = showView

  def getAddTime:Date = addTime

  def setAddTime(addTime:Date):Unit = this.addTime = addTime

}