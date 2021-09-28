import com.github.rjeschke.txtmark.Processor
import sbt.Def.spaceDelimited

lazy val pluginId = "com.refactorings.ruby.RubyRefactorings"
lazy val pluginName = "RubyRefactorings"
lazy val sinceBuild = "202.8194.7"
lazy val currentBuild = "212.5457.6" // see https://plugins.jetbrains.com/plugin/1293-ruby/versions/stable
lazy val untilBuild = "213.*"
lazy val lastReleasedVersion = "0.1.13"
lazy val currentVersion = lastReleasedVersion + sys.env.getOrElse("VERSION_SUFFIX", "")

intellijPluginName in ThisBuild := pluginName
intellijBuild in ThisBuild := currentBuild
intellijPlatform in ThisBuild := IntelliJPlatform.IdeaUltimate

onChangedBuildSource in Scope.Global := ReloadOnSourceChanges

lazy val RubyRefactorings = project.in(file("."))
  .enablePlugins(SbtIdeaPlugin)
  .settings(
    name := pluginName,
    version := currentVersion,
    javacOptions in Compile := Seq(
      "--release", "8",
      "-Xlint:unchecked"
    ),
    scalaVersion := "2.13.6",
    intellijPlugins += s"org.jetbrains.plugins.ruby:${currentBuild}".toPlugin,
    patchPluginXml := pluginXmlOptions { xml =>
      xml.version = currentVersion
      xml.sinceBuild = sinceBuild
      xml.untilBuild = untilBuild
      xml.changeNotes = s"<![CDATA[${
        Processor.process(new File("CHANGELOG.md"))
      }]]>"
    },
    libraryDependencies ++= Seq(
      "com.github.sbt" % "junit-interface" % "0.13.2" % Test,
      "io.sentry" % "sentry" % "5.2.0",
      "org.json4s" %% "json4s-native" % "4.0.3",
    ),
    scalacOptions ++= Seq("-deprecation", "-feature", "-target:jvm-1.8")
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
