package com.refactorings.ruby.plugin

import com.intellij.ide.plugins.{IdeaPluginDescriptor, PluginManagerCore}
import com.intellij.openapi.extensions.PluginId

object RubyRefactorings {
  val pluginId: String = "com.refactorings.ruby.RubyRefactorings"

  lazy val pluginDescriptor: IdeaPluginDescriptor =
    PluginManagerCore.getPlugin(
      // We do this because findId is overloaded:
      // - findId(String idString)
      // - findId(String ...idStrings)
      // and we want to use the latter, for compatibility reasons
      // (the former was added in version 212.4321.30)
      PluginId.findId(List(pluginId): _*)
    )

  def pluginVersion: String = pluginDescriptor.getVersion
}
