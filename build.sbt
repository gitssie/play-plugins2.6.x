import sbt.ClasspathDep
import NativePackagerHelper._
import sbt.Keys.mappings
import play.sbt.PlayImport.PlayKeys._
import com.typesafe.sbt.web.Import.WebKeys._
import sbt._

name := """play-plugins"""

organization := "play"

version := "1.0"

lazy val DefaultSettings:Seq[Def.SettingsDefinition] = Seq(
    generateReverseRouter := false,
    scalaVersion := "2.12.2",
    sources in (Compile, doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false,
    mappings in Universal :={
        val universalMappings = (mappings in Universal).value
        val resourceMappings = (playExternalizedResources in Compile).value
        val filters = universalMappings.filterNot(f => f._2.startsWith("lib/com.ulopay") || f._2.startsWith("bin"))
        val plugins = universalMappings.filter(_._2.startsWith("lib/com.ulopay")).map(f => (f._1 -> ("modules/" + f._1.name)))
        filters ++ plugins ++ contentOf("scripts") ++ resourceMappings.map(f => f._1 -> ("conf/" + f._2))
    },
    externalizeResources :=false,
    importDirectly :=true,
    //directWebModules in Assets ++= Seq("pay-routes","bootstrap"),
    deduplicators += {files:Seq[File] => Some(files.head)},
    resolvers += "Sonatype OSS Snapshots" at "http://oss.szulodev.com/repository/maven-releases/",
    publishTo := Some("Sonatype Snapshots Nexus" at "http://oss.szulodev.com/repository/maven-releases/"),
    credentials += Credentials("Sonatype Nexus Repository Manager", "oss.szulodev.com", "jessie_6", "Micrt741@123")
) ++ inConfig(Assets)(
    deduplicators += {files:Seq[File] => Some(files.head)}
)

lazy val PlayRoutes = (project in file("src/play-routes")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlayDeadbolt = (project in file("src/play-deadbolt")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlaySpring = (project in file("src/play-spring")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlayTrace = (project in file("src/play-trace")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlayConfig = (project in file("src/play-config")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlayZookeeper = (project in file("src/play-zookeeper")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlayConsul = (project in file("src/play-consul")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlayConfigZk = (project in file("src/play-config-zk")).enablePlugins(PlayScala).settings(DefaultSettings : _*).dependsOn(PlayConfig,PlayZookeeper)

lazy val PlayConfigCl = (project in file("src/play-config-cl")).enablePlugins(PlayScala).settings(DefaultSettings : _*).dependsOn(PlayConfig,PlayConsul)

lazy val PlayDdsl = (project in file("src/play-ddsl")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlayDdslZk = (project in file("src/play-ddsl-zk")).enablePlugins(PlayScala).settings(DefaultSettings : _*).dependsOn(PlayDdsl,PlayZookeeper)

lazy val PlayDdslCl = (project in file("src/play-ddsl-cl")).enablePlugins(PlayScala).settings(DefaultSettings : _*).dependsOn(PlayDdsl,PlayConsul)

lazy val PlayFreemarker = (project in file("src/play-freemarker")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlayLibs = (project in file("src/play-libs")).enablePlugins(PlayMinimalJava).settings(DefaultSettings : _*)

lazy val PlayTransport = (project in file("src/play-transport")).enablePlugins(PlayMinimalJava).settings(DefaultSettings : _*).dependsOn(PlayTrace,PlayLibs)

lazy val PlayAkkaJobs = (project in file("src/play-akkajobs")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlayGuard = (project in file("src/play-guard")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlayThrift = (project in file("src/play-thrift")).enablePlugins(PlayScala).settings(DefaultSettings : _*).dependsOn(PlayTransport)

lazy val PlayHttpProxy = (project in file("src/play-httpproxy")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlayGuice = (project in file("src/play-guice")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlayRest = (project in file("src/play-rest")).enablePlugins(PlayScala).settings(DefaultSettings : _*).dependsOn(PlayTransport)

lazy val PlayChangestream = (project in file("src/play-changestream")).enablePlugins(PlayScala).settings(DefaultSettings : _*).dependsOn(PlayLibs)

lazy val PlayRabbitmq = (project in file("src/play-rabbitmq")).enablePlugins(PlayScala).settings(DefaultSettings : _*).dependsOn(PlayLibs)

lazy val PlaySysguard = (project in file("src/play-sysguard")).enablePlugins(PlayScala).settings(DefaultSettings : _*).dependsOn(PlayDeadbolt,PlayRoutes,PlayChangestream,PlayRabbitmq,PlayRest)

lazy val PlayForms = (project in file("src/play-forms")).enablePlugins(PlayScala).settings(DefaultSettings : _*)

lazy val PlayKafka = (project in file("src/play-kafka")).enablePlugins(PlayScala).settings(DefaultSettings : _*).dependsOn(PlayRoutes,PlayLibs,PlayGuice)

lazy val PlayAssets = (project in file("src/play-assets")).enablePlugins(PlayScala).settings(DefaultSettings : _*).dependsOn(PlayRoutes)

lazy val PlayPrometheus = (project in file("src/play-prometheus")).enablePlugins(PlayScala).settings(DefaultSettings : _*).dependsOn(PlayRoutes)

lazy val dependProjects = Seq[ClasspathDep[ProjectReference]](
    PlayRoutes,
    PlayDeadbolt,
    PlayTrace,
    PlayDdsl,
    PlayDdslZk,
    PlayDdslCl,
    PlayConfig,
    PlayZookeeper,
    //PlayConsul,
    PlayConfigZk,
    PlayFreemarker,
    PlayLibs,
    PlayTransport,
    PlayAkkaJobs,
    PlayGuard,
    PlayThrift,
    PlayConfigCl,
    PlayRest,
    PlayForms,
    PlayKafka,
    PlayAssets,
    PlayPrometheus
)


lazy val PlayTest = (project in file("src/play-test")).enablePlugins(PlayJava).settings(DefaultSettings : _*).dependsOn(dependProjects :_*)

lazy val publishedProjects = Seq[ProjectReference](
    PlayRoutes,
    PlayDeadbolt,
    PlaySpring,
    PlayTest
)

lazy val root = (project in file(".")).enablePlugins(PlayScala).settings(DefaultSettings : _*)
  .dependsOn(dependProjects :_*)
  .aggregate(publishedProjects :_*)

//libraryDependencies += guice

// Test Database
//libraryDependencies += "com.h2database" % "h2" % "1.4.194"
//libraryDependencies += "com.kenshoo" %% "metrics-play" % "2.6.2_0.6.1"
//libraryDependencies += "com.digitaltangible" %% "play-guard" % "2.1.0"
//libraryDependencies += "be.objectify" %% "deadbolt-java" % "2.6.1"
//libraryDependencies += "org.springframework" % "spring-context" % "4.3.10.RELEASE"
//libraryDependencies += "com.actimust"% "play-spring-loader" % "1.0.0"

// Testing libraries for dealing with CompletionStage...
//libraryDependencies += "org.assertj" % "assertj-core" % "3.6.2" % Test
//libraryDependencies += "org.awaitility" % "awaitility" % "2.0.0" % Test
// Make verbose tests
testOptions in Test := Seq(Tests.Argument(TestFrameworks.JUnit, "-a", "-v"))
