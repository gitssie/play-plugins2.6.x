name := """play-deadbolt"""

organization := "com.github.gitssie"

version := "2.6.15"

scalaVersion := "2.12.2"

libraryDependencies += cacheApi
libraryDependencies += jdbc


libraryDependencies ++= Seq(
  "be.objectify" %% "deadbolt-scala" % "2.6.0"
)