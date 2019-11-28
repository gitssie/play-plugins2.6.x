package play.api.deadbolt

trait Role extends java.io.Serializable{
  def id: String
  def name: String
  def value:String

  def getId:String
  def getName:String
  def getValue:String
}
