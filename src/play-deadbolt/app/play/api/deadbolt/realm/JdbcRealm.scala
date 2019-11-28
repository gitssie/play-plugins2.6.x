package play.api.deadbolt.realm

import java.sql.ResultSet
import javax.inject.{Inject, Singleton}

import play.api.Configuration
import play.api.db.{DBApi, Database}
import play.api.deadbolt.{Permission, Role, Subject}
import play.api.deadbolt.authz._
import play.api.inject.Injector
import play.api.inject.bind
import play.db.NamedDatabaseImpl

import scala.collection.mutable


/**
  * play.deadbolt.authz.realm.class = "jdbc"
  * play.deadbolt.authz.realm.jdbc.database = "default"
  */

@Singleton
class JdbcRealm @Inject() (inject:Injector,conf:Configuration,defaultDatabase:Database) extends Realm {
  private val confPrefix = "play.deadbolt.authz.realm.jdbc"
  override def getName = classOf[JdbcRealm].getSimpleName

  lazy val database:Database = conf.getOptional[String](confPrefix+".database").map{name =>
    val key = bind[Database].qualifiedWith(new NamedDatabaseImpl(name))
    inject.instanceOf(key)
  }.getOrElse(defaultDatabase)

  lazy val findSubjectSql = conf.getOptional[String](confPrefix+".subjectSql").getOrElse("select id,username,nickname,password from users where username = ?")
  lazy val findRolesSql = conf.getOptional[String](confPrefix+".rolesSql").getOrElse("select id,name,value from user_roles where user_id = ?")
  lazy val findPermissionsSql = conf.getOptional[String](confPrefix+".permissionsSql").getOrElse("select id,name,value from roles_permissions where role_id in (?)")

  override def getAuthenticationInfo(token: AuthenticationToken, passwordService: PasswordService) = {
    if(token.getPrincipal == null || token.getCredentials == null){
      Right("Null username are not allowed by this realm.")
    }else{
      database.withConnection{conn =>
        val st = conn.prepareStatement(findSubjectSql)
        st.setString(1,token.getPrincipal.toString.trim)
        val rs = st.executeQuery()
        var foundResult = false
        var password:String = "<empty>"
        var subject:Subject = null

        while(rs.next()){
          if(foundResult){
            throw new RuntimeException("More than one user row found for user [" + token.getPrincipal + "]. Usernames must be unique.")
          }

          val id = rs.getString(1)
          val username = rs.getString(2)
          val nickname = rs.getString(3)
          password = rs.getString(4)

          subject = new SimpleSubject(id,username,nickname,true)

          foundResult = true
        }
        rs.close()
        st.close()

        if(subject != null && passwordService.passwordsMatch(token.getCredentials,password)){
            Left(subject)
        }else{
            Right("User ["+ token.getPrincipal +"] not found,please check your username and password")
        }
      }
    }
  }

  override def getRoles(subject: Subject):Seq[Role] = {
    database.withConnection{conn =>
      val st = conn.prepareStatement(findRolesSql)
      st.setString(1,subject.id.trim)

      val rs = st.executeQuery()
      val roles = mutable.ArrayBuffer.empty[Role]

      while(rs.next()){
        roles += new SimpleRole(rs.getString(1),rs.getString(2),rs.getString(3))
      }

      rs.close()
      st.close()
      roles
    }
  }

  override def getPermissions(subject: Subject):Seq[Permission] = {
    database.withConnection{conn =>
      val st = conn.prepareStatement(findPermissionsSql)

      val queryParam:String = subject.roles.map(_.id).mkString(",")
      st.setString(1,queryParam)

      val rs = st.executeQuery()
      val permissions = mutable.ArrayBuffer.empty[Permission]

      while(rs.next()){
        permissions += new SimplePermission(rs.getString(1),rs.getString(2),rs.getString(3))
      }

      rs.close()
      st.close()
      permissions
    }
  }
}
