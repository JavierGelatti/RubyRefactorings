import com.github.rjeschke.txtmark.Processor
import sbt.Def.spaceDelimited

import java.net.URI

lazy val pluginId = "com.refactorings.ruby.RubyRefactorings"
lazy val pluginName = "RubyRefactorings"
lazy val sinceBuild = "262.10968.63"
lazy val currentBuild = "262.10968.63" // see https://plugins.jetbrains.com/plugin/1293-ruby/versions/stable
lazy val untilBuild = "262.*"
lazy val scalaVersionNumber = "2.13.18" // see https://www.scala-lang.org/download/all.html
lazy val lastReleasedVersion = "0.4.0"
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
      "--release", "25",
      "-Xlint:unchecked"
    ),
    scalaVersion := scalaVersionNumber,
    intellijPlugins ++= Seq(
      s"org.jetbrains.plugins.ruby:${currentBuild}".toPlugin(transitive = false),
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
      "io.sentry" % "sentry" % "8.59.0", // see https://mvnrepository.com/artifact/io.sentry/sentry
      "io.github.json4s" %% "json4s-native" % "4.1.1",
    ) ++ Seq(
      // the IDE distribution doesn't include testFramework.jar anymore (since 2026.2)
      "test-framework",
      "test-framework-common",
      "test-framework-core",
    ).map("com.jetbrains.intellij.platform" % _ % currentBuild % Test intransitive()),
    resolvers += "IntelliJ Platform Releases" at "https://www.jetbrains.com/intellij-repository/releases",
    scalacOptions ++= Seq("-deprecation", "-feature", "-release:25"),
    intellijExtraRuntimePluginsInTests ++= Seq(
      "com.intellij.modules.ultimate".toPlugin,
      "intellij.libraries.misc.plugin".toPlugin, // provides intellij.libraries.commons.text, required by the Ruby plugin
      "intellij.testRunner.plugin".toPlugin, // provides intellij.platform.smRunner, required by the Ruby plugin
      "intellij.structureView.plugin".toPlugin, // provides intellij.platform.structureView, required by the Ruby plugin
    ),
    // sbt-idea-plugin doesn't add the junit*-rt.jar files (which contain com.intellij.rt.junit.JUnitStarter) to the
    // test classpath, so running tests from the IDE (through the generated JUnit run configuration template) fails
    Test / unmanagedJars ++= {
      val junitRtJarsDirectory = intellijBaseDirectory.value / "plugins" / "junit" / "lib"
      Seq("junit-rt.jar", "junit5-rt.jar", "junit6-rt.jar")
        .map(junitRtJarsDirectory / _)
        .filter(_.exists())
        .map(Attributed.blank)
    },
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
