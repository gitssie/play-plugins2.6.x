package play.api.deadbolt.realm

import javax.inject.{Inject, Provider, Singleton}

import play.api.{Configuration, Environment}
import play.api.deadbolt.Subject
import play.api.deadbolt.authz.{AuthenticationToken, PasswordService}
import play.api.inject.Injector
import play.utils.Reflect


/**
  * config by
  *
  * play.deadbolt.authz.realm.class = "jdbc"
  * play.deadbolt.authz.realm.jdbc = ""
  */

@Singleton
class RealmProvider @Inject() (inject:Injector,env:Environment,conf:Configuration) extends Provider[Realm]{
  val defaultCls = Map("config" -> classOf[ConfigRealm],"jdbc" -> classOf[JdbcRealm])
  override def get():Realm = conf.getOptional[String]("play.deadbolt.authz.realm.class").map(clazz => {
    try{
      defaultCls.find(_._1 == clazz).map(c => inject.instanceOf(c._2)).getOrElse{
        inject.instanceOf(Reflect.getClass[Realm](clazz,env.classLoader))
      }
    }catch {
      case e: ClassNotFoundException =>
        throw conf.reportError("play.deadbolt.authz.realm.class", "Realm not found: " + clazz)
    }
  }).getOrElse(new NoopRealm)
}

class NoopRealm extends Realm {
  /**
    * Returns the (application-unique) name assigned to this <code>Realm</code>. All realms configured for a single
    * application must have a unique name.
    *
    * @return the (application-unique) name assigned to this <code>Realm</code>.
    */
  override def getName = classOf[NoopRealm].getSimpleName

  /**
    * Returns an account's authentication-specific information for the specified <tt>token</tt>,
    * or <tt>null</tt> if no account could be found based on the <tt>token</tt>.
    *
    * <p>This method effectively represents a login attempt for the corresponding user with the underlying EIS datasource.
    * Most implementations merely just need to lookup and return the account data only (as the method name implies)
    * and let Shiro do the rest, but implementations may of course perform eis specific login operations if so
    * desired.
    *
    * @param token the application-specific representation of an account principal and credentials.
    * @return the authentication information for the account associated with the specified <tt>token</tt>,
    *         or <tt>null</tt> if no account could be found.
    *         if there is an error obtaining or constructing an AuthenticationInfo object based on the
    *         specified <tt>token</tt> or implementation-specific login behavior fails.
    */
  override def getAuthenticationInfo(token: AuthenticationToken, passwordService: PasswordService) = Right(getName)

  override def getRoles(subject: Subject) = List.empty

  override def getPermissions(subject: Subject) = List.empty
}