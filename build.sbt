import com.github.rjeschke.txtmark.Processor
import sbt.Def.spaceDelimited

lazy val pluginId = "com.refactorings.ruby.RubyRefactorings"
lazy val pluginName = "RubyRefactorings"
lazy val sinceBuild = "223.7571.4"
lazy val currentBuild = "231.9392.1" // see https://plugins.jetbrains.com/plugin/1293-ruby/versions/stable
lazy val untilBuild = "233.*"
lazy val scalaVersionNumber = "2.13.14" // see https://www.scala-lang.org/download/all.html
lazy val lastReleasedVersion = "0.1.22"
lazy val currentVersion = lastReleasedVersion + sys.env.getOrElse("VERSION_SUFFIX", "")

ThisBuild / intellijPluginName := pluginName
ThisBuild / intellijBuild := currentBuild
ThisBuild / intellijPlatform := IntelliJPlatform.IdeaUltimate

onChangedBuildSource in Scope.Global := ReloadOnSourceChanges

lazy val RubyRefactorings = project.in(file("."))
  .enablePlugins(SbtIdeaPlugin)
  .settings(
    name := pluginName,
    version := currentVersion,
    Compile / javacOptions := Seq(
      "--release", "8",
      "-Xlint:unchecked"
    ),
    scalaVersion := scalaVersionNumber,
    intellijPlugins ++= Seq(
      s"org.jetbrains.plugins.ruby:${currentBuild}".toPlugin,
      "org.jetbrains.plugins.yaml".toPlugin, // a dependency of the Ruby plugin
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
      "io.sentry" % "sentry" % "7.13.0", // see https://mvnrepository.com/artifact/io.sentry/sentry
      "org.json4s" %% "json4s-native" % "4.0.7",
    ),
    scalacOptions ++= Seq("-deprecation", "-feature", "-release:8")
  )

lazy val generateUpdatePluginsXml = inputKey[Unit]("Generate updatePlugins.xml file for custom repository")
generateUpdatePluginsXml := {
  val pluginRepoBaseUrlAsString = spaceDelimited("<repo-base-url>").parsed.headOption

  require(pluginRepoBaseUrlAsString.isDefined, "<repo-base-url> argument missing")

  val pluginRepoBaseUrl = new URL(pluginRepoBaseUrlAsString.get)
  val zipFileName = s"${pluginName}-${currentVersion}.zip"
  val zipFileUrl = new URL(pluginRepoBaseUrl, zipFileName)

  CustomRepositoryGenerator.generateUpdatePluginsXml(
    pluginId,
    pluginName,
    zipFileUrl,
    sinceBuild,
    untilBuild,
    target.value
  )
}
