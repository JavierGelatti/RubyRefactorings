import org.jetbrains.sbtidea.Keys.intellijVMOptions

intellijPluginName in ThisBuild := "RubyRefactorings"
intellijBuild in ThisBuild := "2020.2.3"
intellijPlatform in ThisBuild := IntelliJPlatform.IdeaUltimate

onChangedBuildSource in Scope.Global := ReloadOnSourceChanges

lazy val RubyRefactorings = project.in(file("."))
  .enablePlugins(SbtIdeaPlugin)
  .settings(
    name := "RubyRefactorings",
    version := "0.1",
    scalaVersion := "2.13.3",
    intellijPlugins += "org.jetbrains.plugins.ruby:202.7660.26".toPlugin,
    intellijDownloadSources := true,
    intellijVMOptions := intellijVMOptions.value.add(
      "--add-exports=java.base/jdk.internal.vm=ALL-UNNAMED"
    )
  )

lazy val ideaRunner = createRunnerProject(RubyRefactorings)
