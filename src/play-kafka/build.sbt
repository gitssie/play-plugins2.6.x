name := """play-kafka"""

organization := "com.github.gitssie"

version := "2.6.6"

scalaVersion := "2.12.2"

libraryDependencies += guice

libraryDependencies += "com.typesafe.akka" %% "akka-stream-kafka" % "0.20"