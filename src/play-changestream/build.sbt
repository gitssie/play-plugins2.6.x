name := """play-changestream"""

organization := "com.github.gitssie"

version := "2.6.7"

scalaVersion := "2.12.2"

libraryDependencies += jdbc
libraryDependencies += ws
libraryDependencies += "mysql" % "mysql-connector-java" % "5.1.38"
libraryDependencies += "com.github.shyiko" % "mysql-binlog-connector-java" % "0.13.0"


