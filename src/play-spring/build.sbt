name := """play-spring"""

organization := "com.github.gitssie"

version := "2.6.6"

scalaVersion := "2.12.2"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play" % "2.6.6",
  "org.springframework" % "spring-context" % "4.3.5.RELEASE",
  "org.springframework" % "spring-core" % "4.3.5.RELEASE",
  "org.springframework" % "spring-expression" % "4.3.5.RELEASE",
  "org.springframework" % "spring-aop" % "4.3.5.RELEASE"
)