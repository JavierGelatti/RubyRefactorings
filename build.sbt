import com.github.rjeschke.txtmark.Processor

intellijPluginName in ThisBuild := "RubyRefactorings"
intellijBuild in ThisBuild := "202.7660.26"
intellijPlatform in ThisBuild := IntelliJPlatform.IdeaUltimate

onChangedBuildSource in Scope.Global := ReloadOnSourceChanges

val currentReleasedVersion = "0.1.1"
lazy val currentVersion = sys.env.get("VERSION_SUFFIX")
  .map(suffix => currentReleasedVersion + suffix)
  .getOrElse(currentReleasedVersion)

lazy val RubyRefactorings = project.in(file("."))
  .enablePlugins(SbtIdeaPlugin)
  .settings(
    name := "RubyRefactorings",
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
      xml.sinceBuild = (intellijBuild in ThisBuild).value
      xml.untilBuild = "203.*"
      xml.changeNotes = s"<![CDATA[${
        Processor.process(new File("CHANGELOG.md"))
      }]]>"
    },
    libraryDependencies ++= Seq(
      "com.novocode" % "junit-interface" % "0.11" % Test
    )
  )

lazy val ideaRunner = createRunnerProject(RubyRefactorings)
