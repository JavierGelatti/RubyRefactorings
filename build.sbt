import com.github.rjeschke.txtmark.Processor
import sbt.Def.spaceDelimited

lazy val pluginId = "com.refactorings.ruby.RubyRefactorings"
lazy val pluginName = "RubyRefactorings"
lazy val sinceBuild = "202.7660.26"
lazy val untilBuild = "203.*"
lazy val lastReleasedVersion = "0.1.9"
lazy val currentVersion = lastReleasedVersion + sys.env.getOrElse("VERSION_SUFFIX", "")

intellijPluginName in ThisBuild := pluginName
intellijBuild in ThisBuild := sinceBuild
intellijPlatform in ThisBuild := IntelliJPlatform.IdeaUltimate

onChangedBuildSource in Scope.Global := ReloadOnSourceChanges

lazy val RubyRefactorings = project.in(file("."))
  .enablePlugins(SbtIdeaPlugin)
  .settings(
    name := pluginName,
    version := currentVersion,
    javacOptions in Compile := Seq(
      "-source", "1.8",
      "-target", "1.8",
      "-Xlint:unchecked"
    ),
    scalaVersion := "2.13.3",
    intellijPlugins += "org.jetbrains.plugins.ruby:202.7660.26".toPlugin,
    patchPluginXml := pluginXmlOptions { xml =>
      xml.version = currentVersion
      xml.sinceBuild = sinceBuild
      xml.untilBuild = untilBuild
      xml.changeNotes = s"<![CDATA[${
        Processor.process(new File("CHANGELOG.md"))
      }]]>"
    },
    libraryDependencies ++= Seq(
      "com.novocode" % "junit-interface" % "0.11" % Test
    ),
    scalacOptions ++= Seq("-deprecation", "-feature")
  )

lazy val ideaRunner = createRunnerProject(RubyRefactorings)

lazy val generateUpdatePluginsXml = inputKey[Unit]("Generate updatePlugins.xml file for custom repository")
generateUpdatePluginsXml := {
  val pluginRepoBaseUrlAsString = spaceDelimited("<repo-base-url>").parsed.headOption

  require(pluginRepoBaseUrlAsString.isDefined, "<repo-base-url> argument missing")

  val pluginRepoBaseUrl = new URL(pluginRepoBaseUrlAsString.get)
  val zipFileName = s"${pluginName}-${currentVersion}.zip"
  val zipFileUrl = new URL(pluginRepoBaseUrl, zipFileName)

  CustomRepositoryGenerator.generateUpdatePlugisXml(
    pluginId,
    pluginName,
    zipFileUrl,
    sinceBuild,
    untilBuild,
    target.value
  )
}
