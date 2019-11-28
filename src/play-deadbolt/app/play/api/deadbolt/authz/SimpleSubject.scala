package play.api.deadbolt.authz

import play.api.deadbolt.{Permission, Role, Subject}

import scala.collection.JavaConverters._

class SimpleSubject(idAsStr:String,nameStr:String,nickNameStr:String,rs:Seq[Role],ps:Seq[Permission],isAuth:Boolean) extends Subject {

  lazy val delegate = new DeadboltSubject(this)

  def this(idAsStr:String,nameStr:String,nickNameStr:String,isAuth:Boolean) = this(idAsStr,nameStr,nickNameStr,Seq.empty,Seq.empty,isAuth)

  def this(idAsStr:String,isAuth:Boolean) = this(idAsStr,idAsStr,idAsStr,isAuth)

  override def id = idAsStr

  override def idAsInt = idAsStr.toInt

  override def idAsLong = idAsStr.toLong

  override def roles = rs

  override def permissions = ps

  override def name = nameStr

  override def nickName = nickNameStr

  override def toDelegate[T]: T = delegate.asInstanceOf[T]

  override def isAuthenticated = isAuth

  def getId = id
  def getName = name
  def getNickName = nickName
  def getRoles = rs.asJava
  def getPermissions = ps.asJava
}

class SimpleRole(idAsStr:String,nameAsStr:String,valueAsStr:String) extends Role {
  override def id = idAsStr
  override def name = nameAsStr
  override def value = valueAsStr

  override def getId:String = id
  override def getName:String = name
  override def getValue:String = value
}

class SimplePermission(idAsStr:String,nameAsStr:String,valueAsStr:String) extends Permission {
  override def id = idAsStr
  override def name = nameAsStr
  override def value = valueAsStr

  override def getId:String = id
  override def getName:String = name
  override def getValue:String = value
}