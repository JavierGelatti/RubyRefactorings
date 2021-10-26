package com.refactorings.ruby.plugin

import com.intellij.ide.plugins.{IdeaPluginDependency, IdeaPluginDescriptor, PluginManagerCore}
import com.intellij.openapi.extensions.PluginId

import scala.jdk.CollectionConverters.CollectionHasAsScala

object RubyRefactorings {
  val pluginName: String = "com.refactorings.ruby.RubyRefactorings"

  lazy val pluginDescriptor: IdeaPluginDescriptor =
    PluginManagerCore.getPlugin(RubyRefactorings.pluginId)

  lazy val pluginId: PluginId = PluginId.findId(
    // We do this because findId is overloaded:
    // - findId(String idString)
    // - findId(String ...idStrings)
    // and we want to use the latter, for compatibility reasons
    // (the former was added in version 212.4321.30)
    List(pluginName): _*
  )

  def pluginVersion: String = pluginDescriptor.getVersion

  def isEnabled: Boolean = pluginDescriptor.isEnabled

  def dependencies: List[IdeaPluginDependency] = pluginDescriptor.getDependencies.asScala.toList
}
