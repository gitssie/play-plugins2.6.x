name := """play-ddsl-zk"""

organization := "com.github.gitssie"

version := "2.6.6"

scalaVersion := "2.12.2"

libraryDependencies += ("org.apache.curator" % "curator-x-discovery" % "4.0.0").exclude("org.apache.zookeeper", "zookeeper")