name := """play-httpproxy"""

organization := "com.github.gitssie"

version := "2.6.6"

scalaVersion := "2.12.2"

libraryDependencies += guice


// https://mvnrepository.com/artifact/org.littleshoot/littleproxy
libraryDependencies += "org.littleshoot" % "littleproxy" % "0.5.3"
// https://mvnrepository.com/artifact/net.schmizz/sshj
libraryDependencies += "net.schmizz" % "sshj" % "0.9.0"

libraryDependencies += "com.typesafe.play" %% "play-mailer" % "6.0.1"
libraryDependencies += "com.typesafe.play" %% "play-mailer-guice" % "6.0.1"