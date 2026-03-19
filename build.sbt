import com.github.rjeschke.txtmark.Processor
import sbt.Def.spaceDelimited

import java.net.URI

lazy val pluginId = "com.refactorings.ruby.RubyRefactorings"
lazy val pluginName = "RubyRefactorings"
lazy val sinceBuild = "251.23774.151"
lazy val currentBuild = "253.32098.37" // see https://plugins.jetbrains.com/plugin/1293-ruby/versions/stable
lazy val untilBuild = "253.*"
lazy val scalaVersionNumber = "2.13.18" // see https://www.scala-lang.org/download/all.html
lazy val lastReleasedVersion = "0.2.0"
lazy val currentVersion = lastReleasedVersion + sys.env.getOrElse("VERSION_SUFFIX", "")

ThisBuild / intellijPluginName := pluginName
ThisBuild / intellijBuild := currentBuild
ThisBuild / intellijPlatform := IntelliJPlatform.IdeaUltimate

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val RubyRefactorings = project.in(file("."))
  .enablePlugins(SbtIdeaPlugin)
  .settings(
    name := pluginName,
    version := currentVersion,
    Compile / javacOptions := Seq(
      "--release", "21",
      "-Xlint:unchecked"
    ),
    scalaVersion := scalaVersionNumber,
    intellijPlugins ++= Seq(
      s"org.jetbrains.plugins.ruby:${currentBuild}".toPlugin,
      "org.jetbrains.plugins.yaml".toPlugin, // a dependency of the Ruby plugin
      "com.intellij.modules.json".toPlugin, // a dependency of the YAML plugin
    ),
    patchPluginXml := pluginXmlOptions { xml =>
      xml.version = currentVersion
      xml.sinceBuild = sinceBuild
      xml.untilBuild = untilBuild
      xml.changeNotes = s"<![CDATA[${
        Processor.process(new File("CHANGELOG.md"))
      }]]>"
    },
    libraryDependencies ++= Seq(
      "com.github.sbt" % "junit-interface" % "0.13.3" % Test,
      "org.opentest4j" % "opentest4j" % "1.3.0" % Test,
      "io.sentry" % "sentry" % "8.34.1", // see https://mvnrepository.com/artifact/io.sentry/sentry
      "io.github.json4s" %% "json4s-native" % "4.1.0",
    ),
    scalacOptions ++= Seq("-deprecation", "-feature", "-release:21"),
    intellijExtraRuntimePluginsInTests ++= Seq(
      "com.intellij.modules.ultimate".toPlugin,
    ),
    buildIntellijOptionsIndex := false
  )

lazy val generateUpdatePluginsXml = inputKey[Unit]("Generate updatePlugins.xml file for custom repository")
generateUpdatePluginsXml := {
  val pluginRepoBaseUrlAsString = spaceDelimited("<repo-base-url>").parsed.headOption

  require(pluginRepoBaseUrlAsString.isDefined, "<repo-base-url> argument missing")

  val pluginRepoBaseUrl = URI.create(pluginRepoBaseUrlAsString.get)
  val zipFileName = s"${pluginName}-${currentVersion}.zip"
  val zipFileUrl = pluginRepoBaseUrl.resolve(zipFileName).toURL

  CustomRepositoryGenerator.generateUpdatePluginsXml(
    pluginId,
    pluginName,
    zipFileUrl,
    sinceBuild,
    untilBuild,
    target.value
  )
}
