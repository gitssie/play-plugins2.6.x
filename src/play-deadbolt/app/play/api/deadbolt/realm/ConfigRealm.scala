package play.api.deadbolt.realm

import javax.inject.{Inject, Singleton}

import play.api.Configuration
import play.api.deadbolt.authz._
import play.api.deadbolt.{Permission, Role, Subject}


@Singleton
class ConfigRealm @Inject() (conf:Configuration) extends Realm {
  private val confPrefix = "play.deadbolt.authz.realm.config"

  override def getName = classOf[ConfigRealm].getSimpleName

  val users = conf.getOptional[String](confPrefix + ".users").getOrElse("").split(",")

  override def getAuthenticationInfo(token: AuthenticationToken, passwordService: PasswordService) = {
    val notFound:Either[Subject,String] = Right("User ["+ token.getPrincipal +"] not found,please check your username and password")
    users.find(_.equalsIgnoreCase(token.getPrincipal.toString)).map{name =>
      val pwd = conf.getOptional[String](confPrefix + "." + name + ".password").getOrElse("")
      if (passwordService.passwordsMatch(token.getCredentials.toString, pwd)) {
        val subject:Subject = new SimpleSubject(name,true)
        Left(subject)
      } else {
        notFound
      }
    }.getOrElse(notFound)
  }

  override def getRoles(subject: Subject):Seq[Role] = {
    conf.getOptional[String](confPrefix + "." + subject.id + ".roles").getOrElse("").split(",").map(role =>
      new SimpleRole(role,role,role)
    )
  }

  override def getPermissions(subject: Subject):Seq[Permission] = {
    conf.getOptional[String](confPrefix + "." + subject.id + ".permissions").getOrElse("").split(",").map(p =>
      new SimplePermission(p,p,p)
    )
  }
}
