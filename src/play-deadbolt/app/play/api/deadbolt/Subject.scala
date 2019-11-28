package play.api.deadbolt

trait Subject extends java.io.Serializable{

  def id:String

  def name:String

  def nickName:String

  def idAsInt:Int

  def idAsLong:Long

  def roles: Seq[Role]

  def permissions: Seq[Permission]

  def toDelegate[T] : T

  def isAuthenticated: Boolean

  def getId:String

  def getName:String

  def getNickName:String

  def getRoles:java.util.List[Role]

  def getPermissions:java.util.List[Permission]
}

