name := """play-sysguard"""

organization := "com.github.gitssie"

version := "2.6.6"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  ws,
  guice,
  ehcache,
  javaForms,
  "org.iq80.leveldb" % "leveldb" % "0.10"
)



