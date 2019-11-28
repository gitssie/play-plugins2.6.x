name := """play-prometheus"""

organization := "com.github.gitssie"

version := "2.6.12"

scalaVersion := "2.12.2"

libraryDependencies += "com.github.stijndehaes" %% "play-prometheus-filters" % "0.4.0"
libraryDependencies += "io.prometheus" % "simpleclient_hotspot" % "0.6.0"
