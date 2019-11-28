package play.api.deadbolt.authz
import javax.inject.{Inject, Singleton}

import play.api.deadbolt.Subject
import play.api.deadbolt.realm.Realm
import play.api.deadbolt.session.Session

@Singleton
class DefaultSecurityManager @Inject () (realm:Realm,passwordService:PasswordService) extends SecurityManager{
  private val subjectKey = "SUBJECT_KEY"
  /**
    * authentication subject and local cached and put in session
    */
  override def login(token: AuthenticationToken): Either[Subject, String] = login(token,passwordService)

  def login(token: AuthenticationToken,passwordService: PasswordService): Either[Subject, String] = {
    val subjectE = realm.getAuthenticationInfo(token,passwordService)
    if(subjectE.isLeft){
      val subject = subjectE.left.get
      Left(subject)
    }else{
      subjectE
    }
  }

  /**
    * clean cached subject
    */
  override def logout(subject: Subject,s: Session): Unit = {
    s.removeAttribute(subjectKey)
  }

  override def encryptPassword(pwd: String): String = passwordService.encryptPassword(pwd)

  override def getSubject(session: Session):Subject = Option(session).filter(s => s.getId != null).map(s =>{
    s.getAttribute(subjectKey).asInstanceOf[Subject]
  }).getOrElse(null)

  override def attachSubject(subject: Subject, map: java.util.Map[String,AnyRef]):Unit = {
    map.put(subjectKey,subject)
  }
}
