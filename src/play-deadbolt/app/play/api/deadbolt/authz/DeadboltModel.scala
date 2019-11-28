package play.api.deadbolt.authz

import be.objectify.deadbolt.scala.models.{Permission, Role, Subject}

private [deadbolt] class DeadboltSubject(subject:play.api.deadbolt.Subject) extends Subject{

  override def identifier:String = subject.id

  override lazy val roles :List[Role]= subject.roles.map(new DeadboltRole(_)).toList

  override lazy val permissions : List[Permission]= subject.permissions.map(new DeadboltPermission(_)).toList
}

private [deadbolt] class DeadboltRole(role:play.api.deadbolt.Role) extends Role{
  override def name = role.value
}

private [deadbolt] class DeadboltPermission(permission: play.api.deadbolt.Permission) extends Permission {
  override def value = permission.value
}
