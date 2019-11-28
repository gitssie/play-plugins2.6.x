package play.api.deadbolt.authz

import javax.inject.{Inject, Singleton}

import org.apache.commons.lang3.StringUtils
import play.api.deadbolt.session.Session
import play.api.deadbolt.session.mgt.{DefaultSessionKey, MapSession, SessionContext, SessionManager}
import play.api.deadbolt.{Authz, Subject}
import play.api.http.SessionConfiguration
import play.mvc.Http

@Singleton
class DefaultAuthz @Inject() (securityManager: SecurityManager,sessionManager: SessionManager,sessionCfg: SessionConfiguration) extends Authz{
  private val anonymous:Subject = new SimpleSubject("anonymous","anonymous","<anonymous>",false)

  override def getSubject[A >:Subject](request: Http.Session):A = {
    val session = getSession(request)
    var subject:Subject = securityManager.getSubject(session)
    if(subject == null){
      subject = anonymous
    }
    subject
  }

  override def getSession(request: Http.Session):Session = {
    val sessionId = request.get(sessionCfg.cookieName)
    var session:Session = null
    if(StringUtils.isNoneEmpty(sessionId)){
      session = sessionManager.getSession(new DefaultSessionKey(sessionId))
    }
    if(session == null){
      session = toMapSession(request)
    }
    session
  }

  def toMapSession(request: Http.Session):Session = new MapSession(request)

  def login[A >:Subject](token:AuthenticationToken,request:play.mvc.Http.Session):Either[A,String] = {
    val subjectE = securityManager.login(token)
    if(subjectE.isLeft){
      val map:java.util.Map[String,AnyRef] = new java.util.HashMap[String,AnyRef]()
      map.putAll(request)
      securityManager.attachSubject(subjectE.left.get,map)
      val session = sessionManager.start(new SessionContext(map))
      request.put(sessionCfg.cookieName,session.getId.toString)
    }
    subjectE
  }

  def logout(subject: Subject,request:play.mvc.Http.Session):Unit = {
    val session = getSession(request)
    securityManager.logout(subject,session)
    sessionManager.destroy(session)
  }

  override def encryptPassword(pwd: String):String = securityManager.encryptPassword(pwd)

  def touch(session:Session): Unit = {
      if(session.getId != null){
        sessionManager.touch(session)
      }
  }
}
