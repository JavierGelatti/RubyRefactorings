package com.refactorings.ruby.plugin

import com.intellij.ide.plugins.PluginManagerCore
import com.refactorings.ruby.plugin.RubyRefactorings.pluginVersion

sealed abstract class Environment(val name: String)
case object TestEnvironment extends Environment("test")
case object DevelopmentEnvironment extends Environment("development")
case object StagingEnvironment extends Environment("staging")
case object ProductionEnvironment extends Environment("production")

object Environment {
  lazy val current: Environment = {
    if (PluginManagerCore.isUnitTestMode) {
      TestEnvironment
    } else if (pluginVersion == "0.1") {
      DevelopmentEnvironment
    } else if (pluginVersion.split('.').length > 3) {
      StagingEnvironment
    } else {
      ProductionEnvironment
    }
  }
}
