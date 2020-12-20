package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Extensions.{MethodExtension, PsiElementExtension}
import com.refactorings.ruby.psi.Parser.parseHeredoc
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RObjectClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.{RMethod, RSingletonMethod}

class ReplaceDefSelfByOpeningSingletonClass extends RefactoringIntention(ReplaceDefSelfByOpeningSingletonClass) {
  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val singletonMethodToRefactor: RSingletonMethod = findSingletonMethodEnclosing(focusedElement).get
    singletonMethodToRefactor.normalizeSpacesAfterParameterList()

    val openSingletonClassTemplate = parseHeredoc(
    """
     |class << OBJECT
     |  def METHOD_NAME
     |    METHOD_BODY
     |  end
     |end
    """)
    val openSingletonClass = openSingletonClassTemplate.childOfType[RObjectClass]()
    val newMethodDefinition = openSingletonClassTemplate.childOfType[RMethod]()

    copyParametersAndBody(source = singletonMethodToRefactor, target = newMethodDefinition)

    openSingletonClass.getObject.replace(singletonMethodToRefactor.getClassObject)
    singletonMethodToRefactor.replace(openSingletonClass)
  }

  private def copyParametersAndBody
    (source: RSingletonMethod, target: RMethod)
    (implicit project: Project)
  = {
    val sourceName = source.getMethodName
    val sourceBody = source.body
    val targetBody = target.body

    target.getMethodName.setName(source.getNameIdentifier.getText)

    val targetArgumentList = target.getArgumentList
    target.addRangeBefore(sourceName.getNextSibling, sourceBody.getPrevSibling.getPrevSibling, targetArgumentList)
    targetArgumentList.delete()

    targetBody.replace(sourceBody)
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    findSingletonMethodEnclosing(element).isDefined
  }

  private def findSingletonMethodEnclosing(element: PsiElement) = {
    element.findParentOfType[RSingletonMethod](treeHeightLimit = 4)
  }
}

object ReplaceDefSelfByOpeningSingletonClass extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Replace def self by opening singleton class"
  override def optionDescription = "Open singleton class instead"
}
