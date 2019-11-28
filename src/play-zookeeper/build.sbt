name := """play-zookeeper"""

organization := "com.github.gitssie"

version := "2.6.11"

scalaVersion := "2.12.2"

// https://mvnrepository.com/artifact/org.apache.curator/curator-x-discovery
libraryDependencies += ("org.apache.curator" % "curator-framework" % "4.0.0").exclude("org.apache.zookeeper", "zookeeper")
libraryDependencies += "org.apache.zookeeper" % "zookeeper" % "3.4.10"
libraryDependencies += "org.apache.curator" % "curator-recipes" % "4.0.0"
