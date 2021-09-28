package com.refactorings.ruby

import com.intellij.ide.plugins.{IdeaPluginDescriptor, PluginManagerCore}
import com.intellij.openapi.extensions.PluginId

object RubyRefactorings {
  private val pluginId = "com.refactorings.ruby.RubyRefactorings"

  lazy val pluginDescriptor: IdeaPluginDescriptor =
    PluginManagerCore.getPlugin(PluginId.findId(pluginId))

  def pluginVersion: String = pluginDescriptor.getVersion
}
