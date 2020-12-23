package com.refactorings.ruby

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.Variable

package object ui {
  implicit class TemplateExtension(template: Template) {
    def addVariable(variable: TemplateVariable): Variable = {
      variable match {
        case v: MainVariable =>
          template.addVariable(variable.variableName, v.expression, v.expression, true)
        case v: ReplicaVariable =>
          template.addVariableSegment(v.variableName)
          template.addVariable(v.variableName, v.dependantVariableName, v.dependantVariableName, false)
      }
    }
  }
}
