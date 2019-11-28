package play.api.deadbolt.authz

import play.api.deadbolt.Subject
import play.api.deadbolt.session.Session

trait SecurityManager {

  def attachSubject(subject: Subject, map: java.util.Map[String,AnyRef])

  def getSubject(session: Session):Subject

  def login(token:AuthenticationToken):Either[Subject,String]

  def logout(subject: Subject,session: Session): Unit

  def encryptPassword(pwd:String):String
}
