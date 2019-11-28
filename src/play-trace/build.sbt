name := """play-trace"""

organization := "com.github.gitssie"

version := "2.6.6"

scalaVersion := "2.12.2"

libraryDependencies += guice

libraryDependencies ++= Seq(
  "io.zipkin.brave" % "brave" % "4.8.1",
  "io.zipkin.brave" % "brave-context-slf4j" % "4.8.1",
  "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % "2.0.2"
)