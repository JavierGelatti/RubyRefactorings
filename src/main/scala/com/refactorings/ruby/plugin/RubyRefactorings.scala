package com.refactorings.ruby.plugin

import com.intellij.ide.plugins.{IdeaPluginDependency, IdeaPluginDescriptor, PluginManagerCore}
import com.intellij.openapi.extensions.PluginId

import scala.jdk.CollectionConverters.CollectionHasAsScala

object RubyRefactorings {
  val pluginName: String = "com.refactorings.ruby.RubyRefactorings"

  lazy val pluginDescriptor: IdeaPluginDescriptor =
    PluginManagerCore.getPlugin(RubyRefactorings.pluginId)

  lazy val pluginId: PluginId = PluginId.getId(pluginName)

  def pluginVersion: String = pluginDescriptor.getVersion

  def isEnabled: Boolean = !PluginManagerCore.isDisabled(pluginId)

  def dependencies: List[IdeaPluginDependency] = pluginDescriptor.getDependencies.asScala.toList
}
