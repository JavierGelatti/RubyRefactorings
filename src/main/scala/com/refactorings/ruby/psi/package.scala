package com.refactorings.ruby

import com.intellij.openapi.editor.{Document, Editor, RangeMarker, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiComment, PsiDocumentManager, PsiElement, PsiNamedElement, PsiWhiteSpace}
import com.refactorings.ruby.psi.Matchers._
import com.refactorings.ruby.psi.Parser.parse
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.scope.ScopeUtil
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.lexer.ruby19.Ruby19TokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.{RStringLiteral, RWords}
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.{RNumericConstant, RSymbol}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures._
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks._
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.{ArgumentInfo, RMethod, Visibility}
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.{RBlockCall, RBraceBlockCall, RCodeBlock}
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.{RArgumentToBlock, RCall, RubyCallTypes}
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RDotReference
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.fields.{RClassVariable, RInstanceVariable}
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.{RFid, RIdentifier, RPseudoConstant}
import org.jetbrains.plugins.ruby.ruby.lang.psi.visitors.RubyRecursiveElementVisitor
import org.jetbrains.plugins.ruby.ruby.lang.psi.{RFile, RPossibleCall, RPsiElement, RubyPsiUtil}

import java.util
import scala.PartialFunction.{cond, condOpt}
import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.language.reflectiveCalls
import scala.reflect.ClassTag

package object psi {
  implicit class PsiElementExtension[ElementType <: PsiElement](sourceElement: ElementType) {
    protected implicit lazy val project: Project = sourceElement.getProject

    def contains(otherElement: PsiElement): Boolean = {
      sourceElement.getTextRange.contains(otherElement.getTextRange)
    }

    def findParentOfType[T <: PsiElement]
    (treeHeightLimit: Int = -1, matching: T => Boolean = (_: T) => true)
    (implicit tag: ClassTag[T])
    : Option[T] = {
      if (treeHeightLimit == 0) return None

      sourceElement.getParent match {
        case null => None
        case element: T if tag.runtimeClass.isInstance(element) && matching(element) => Some(element)
        case otherElement => otherElement.findParentOfType[T](treeHeightLimit - 1, matching)
      }
    }

    def childOfType[T <: PsiElement]
    (treeHeightLimit: Int = -1, matching: T => Boolean = (_: T) => true)
    (implicit tag: ClassTag[T])
    : T = {
      findChildOfType[T](treeHeightLimit, matching).get
    }

    def findChildOfType[T <: PsiElement]
    (treeHeightLimit: Int = -1, matching: T => Boolean = (_: T) => true)
    (implicit tag: ClassTag[T])
    : Option[T] = {
      if (treeHeightLimit == 0) return None

      val children = allChildren
      children
        .find(element => tag.runtimeClass.isInstance(element) && matching(element.asInstanceOf[T]))
        .map(_.asInstanceOf[T])
        .orElse {
          children
            .view
            .flatMap(directChild => directChild.findChildOfType[T](treeHeightLimit - 1, matching))
            .headOption
        }
    }

    def findChildrenOfType[T <: PsiElement]
    (treeHeightLimit: Int = -1, matching: T => Boolean = (_: T) => true)
    (implicit tag: ClassTag[T])
    : List[T] = {
      if (treeHeightLimit == 0) return List()

      val children = allChildren
      children
        .filter(element => tag.runtimeClass.isInstance(element) && matching(element.asInstanceOf[T]))
        .map(_.asInstanceOf[T])
        .concat(
          children
            .view
            .flatMap(directChild => directChild.findChildrenOfType[T](treeHeightLimit - 1, matching))
        )
    }

    def findConditionalParent(treeHeightLimit: Int = 1): Option[IfOrUnlessStatement] = {
      sourceElement.findParentOfType[RIfStatement](treeHeightLimit)
        .orElse(sourceElement.findParentOfType[RUnlessStatement](treeHeightLimit))
        .map(_.asInstanceOf[IfOrUnlessStatement])
    }

    def replaceWith(elements: PsiElement*): Unit = {
      val parent = sourceElement.getParent
      withoutNewlinesAfterComments(elements).foreach { element =>
        parent.addBefore(element, sourceElement)
      }
      sourceElement.delete()
    }

    // We have to do this before adding a comment to an element, because when adding a line comment
    // a new line is automatically added at the end
    private def withoutNewlinesAfterComments(elements: Seq[PsiElement]): Seq[PsiElement] = {
      elements match {
        case (comment: PsiComment) +: (space: PsiWhiteSpace) +: rest =>
          comment +: parse(s"${space.getText.stripPrefix("\n")}") +: withoutNewlinesAfterComments(rest)
        case element +: rest =>
          element +: withoutNewlinesAfterComments(rest)
        case _ => Seq.empty
      }
    }

    def replaceWithBlock(elementsToReplaceBodyWith: RCompoundStatement): Unit = {
      if (elementsToReplaceBodyWith.hasNoChildren) return sourceElement.delete()

      require(container == sourceElement.getParent, "The element to be replaced with a block is not a statement")
      container.addRangeBefore(
        elementsToReplaceBodyWith.getFirstChild,
        elementsToReplaceBodyWith.getLastChild,
        sourceElement
      )

      val lastAddedElement = sourceElement.getPrevSibling
      sourceElement.delete()

      lastAddedElement match {
        case extraNewLine@Whitespace("\n") => extraNewLine.delete()
        case _ => ()
      }
    }

    def addBlockAfter(blockToAdd: RCompoundStatement, anchor: PsiElement): Unit = {
      val placeholder = sourceElement.addAfter(Parser.nil, anchor)
      placeholder.replaceWithBlock(blockToAdd)
    }

    def hasNoChildren: Boolean = sourceElement.getFirstChild == null

    def putBefore(referenceElement: PsiElement): ElementType = {
      referenceElement.getParent.addBefore(sourceElement, referenceElement).asInstanceOf[ElementType]
    }

    def putAfter(referenceElement: PsiElement): ElementType = {
      referenceElement.getParent.addAfter(sourceElement, referenceElement).asInstanceOf[ElementType]
    }

    def container: PsiElement = {
      findParentOfType[RCompoundStatement]()
        .getOrElse(sourceElement.getParent)
    }

    def isStartOfString: Boolean = {
      cond(sourceElement) {
        case Leaf(RubyTokenTypes.tDOUBLE_QUOTED_STRING_BEG) => true
      }
    }

    def isLastChildOfParent: Boolean = {
      sourceElement.getParent.getLastChild == sourceElement
    }

    def isFlowInterruptionStatement: Boolean = sourceElement match {
      case _: RReturnStatement | _: RBreakStatement | _: RNextStatement => true
      case raiseSend: RCall if raiseSend.isRaise => true
      case _ => false
    }

    def reindent(): Unit = {
      CodeStyleManager.getInstance(project)
        .adjustLineIndent(
          sourceElement.getContainingFile,
          sourceElement.getTextRange
        )
    }

    def referencesInside(scope: PsiElement): List[PsiElement] = {
      ReferencesSearch
        .search(sourceElement, new LocalSearchScope(scope))
        .findAll()
        .asScala
        .map(_.getElement)
        .toList
    }

    def forEachLocalVariableReference(functionToApply: RIdentifier => Unit): Unit = {
      sourceElement.accept(new RubyRecursiveElementVisitor() {
        override def visitRIdentifier(identifier: RIdentifier): Unit = {
          super.visitRIdentifier(identifier)

          if (identifier.isLocalVariable) functionToApply(identifier)
        }
      })
    }

    def forEachSelfReference(functionToApply: RPseudoConstant => Unit): Unit = {
      sourceElement.accept(new RubyRecursiveElementVisitor() {
        override def visitRPseudoConstant(pseudoConstant: RPseudoConstant): Unit = {
          super.visitRPseudoConstant(pseudoConstant)

          if (pseudoConstant.textMatches(RPseudoConstant.SELF)) functionToApply.apply(pseudoConstant)
        }
      })
    }

    def forEachSuperReference(functionToApply: RPseudoConstant => Unit): Unit = {
      sourceElement.accept(new RubyRecursiveElementVisitor() {
        override def visitRPseudoConstant(pseudoConstant: RPseudoConstant): Unit = {
          super.visitRPseudoConstant(pseudoConstant)

          if (pseudoConstant.textMatches(RPseudoConstant.SUPER)) functionToApply.apply(pseudoConstant)
        }
      })
    }

    def forEachMessageSendWithImplicitReceiver(functionToApply: (RPossibleCall with PsiNamedElement) => Unit): Unit = {
      sourceElement.accept(new RubyRecursiveElementVisitor() {
        override def visitRIdentifier(rIdentifier: RIdentifier): Unit = {
          super.visitRIdentifier(rIdentifier)
          if (rIdentifier.isMessageSendWithImplicitReceiver) functionToApply(rIdentifier)
        }

        override def visitRFid(rFid: RFid): Unit = {
          super.visitRFid(rFid)
          if (rFid.isMessageSendWithImplicitReceiver) functionToApply(rFid)
        }
      })
    }

    def forEachClassVariable(functionToApply: RClassVariable => Unit): Unit = {
      sourceElement.accept(new RubyRecursiveElementVisitor() {
        override def visitRClassVariable(classVariable: RClassVariable): Unit = {
          super.visitRClassVariable(classVariable)
          functionToApply(classVariable)
        }
      })
    }

    def forEachInstanceVariable(functionToApply: RInstanceVariable => Unit): Unit = {
      sourceElement.accept(new RubyRecursiveElementVisitor() {
        override def visitRInstanceVariable(instanceVariable: RInstanceVariable): Unit = {
          super.visitRInstanceVariable(instanceVariable)
          functionToApply(instanceVariable)
        }
      })
    }

    def instanceVariableNamed(instanceVariableName: String): Option[RInstanceVariable] = {
      forEachInstanceVariable { instanceVariable =>
        if (instanceVariable.textMatches(instanceVariableName)) {
          return Some(instanceVariable)
        }
      }
      None
    }

    def allChildren: List[PsiElement] = {
      val result = new ListBuffer[PsiElement]

      var currentChild = sourceElement.getFirstChild
      while (currentChild != null) {
        result.addOne(currentChild)
        currentChild = currentChild.getNextSibling
      }

      result.toList
    }

    def siblingsUntilButNotIncluding(finalSibling: PsiElement): List[PsiElement] = {
      if (finalSibling == sourceElement) return List()

      val result = new ListBuffer[PsiElement]

      var currentSibling = sourceElement.getNextSibling
      while (currentSibling != finalSibling && currentSibling != null) {
        result.addOne(currentSibling)
        currentSibling = currentSibling.getNextSibling
      }

      result.toList
    }

    def myselfAndSiblingsUntilAndIncluding(finalSibling: PsiElement): List[PsiElement] = {
      ((sourceElement :: siblingsUntilButNotIncluding(finalSibling)) :+ finalSibling).distinct
    }

    def nextSiblingIgnoringWhitespaceAndNewlines: Option[PsiElement] = {
      var currentSibling = sourceElement.getNextSibling
      while (currentSibling != null) {
        currentSibling match {
          case Whitespace(_) | EndOfLine(_) =>
            currentSibling = currentSibling.getNextSibling
          case _ => return Some(currentSibling)
        }
      }
      None
    }

    def nextSiblingIgnoringWhitespace: Option[PsiElement] = {
      var currentSibling = sourceElement.getNextSibling
      while (currentSibling != null) {
        currentSibling match {
          case Whitespace(_) =>
            currentSibling = currentSibling.getNextSibling
          case _ => return Some(currentSibling)
        }
      }
      None
    }

    def followingComment: Option[PsiComment] = {
      nextSiblingIgnoringWhitespace
        .filter(_.isInstanceOf[PsiComment])
        .map(_.asInstanceOf[PsiComment])
    }

    def staticTruthValue: Option[Boolean] = condOpt(sourceElement) {
      case PseudoConstant("true") =>
        true
      case PseudoConstant("false") | PseudoConstant("nil") =>
        false
      case _: RNumericConstant =>
        true
      case symbol: RSymbol if !symbol.hasExpressionSubstitutions =>
        true
      case string: RStringLiteral if !string.hasExpressionSubstitutions =>
        true
    }

    def astEquivalentTo(anotherElement: PsiElement): Boolean = {
      (sourceElement, anotherElement) match {
        case (a: RIdentifier, b: RIdentifier) =>
          a.textMatches(b.getText)
        case _ if sourceElement.getClass == anotherElement.getClass =>
            sourceElement
              .getChildren
              .zipAll(anotherElement.getChildren, null, null)
              .forall { case (x, y) => x.astEquivalentTo(y) }
        case _ =>
          false
      }
    }

    def isInsideCompoundStatement: Boolean = sourceElement.getParent.isInstanceOf[RCompoundStatement]
  }

  implicit class RPsiElementExtension(sourceElement: RPsiElement) extends PsiElementExtension(sourceElement) {
    def rubyVersion: Double = sourceElement.getLanguageLevel.getCode.toDouble / 10
  }

  type IfOrUnlessStatement = RExpression with RBlockStatement with RConditionalStatement {
    def getThenBlock: RCompoundStatement
    def getElseBlock: RElseBlock
  }

  implicit class IfOrUnlessStatementExtension
  (sourceElement: IfOrUnlessStatement) extends RPsiElementExtension(sourceElement) {
    def hasNoAlternativePaths: Boolean = hasNoElseBlock && hasNoElsifBlocks

    def hasAlternativePaths: Boolean = !hasNoAlternativePaths

    def hasNoElseBlock: Boolean = sourceElement.getElseBlock == null

    def hasNoElsifBlocks: Boolean = getElsifBlocks.isEmpty

    def hasEmptyThenBlock: Boolean = sourceElement.getThenBlock.getStatements.isEmpty

    def keyword: String = sourceElement match {
      case _: RIfStatement => "if"
      case _: RUnlessStatement => "unless"
    }

    def negatedKeyword: String = sourceElement match {
      case _: RIfStatement => "unless"
      case _: RUnlessStatement => "if"
    }

    def condition: Option[RCondition] = Option(sourceElement.getCondition)

    def staticConditionValue: Option[Boolean] = sourceElement.condition
      .flatMap(_.getFirstChild.staticTruthValue)
      .map { conditionValue =>
        if (sourceElement.keyword == "unless") !conditionValue else conditionValue
      }

    def getElsifBlocks: List[RElsifBlock] = {
      sourceElement match {
        case ifStatement: RIfStatement => ifStatement.getElsifBlocks.toList
        case _: RUnlessStatement => List()
      }
    }

    def elseBlock: Option[RElseBlock] = Option(sourceElement.getElseBlock)

    def alternativeBlockPreservingElsifPaths(implicit project: Project): Option[RCompoundStatement] = {
      sourceElement.getElsifBlocks match {
        case firstElsif :: restOfElsifs =>
          val newBody = Parser.parseHeredoc(
            """
              |if CONDITION
              |  THEN_BODY
              |end
            """).asInstanceOf[RCompoundStatement]
          val ifFromNewBody = newBody.childOfType[RIfStatement]()

          ifFromNewBody.getCondition.replace(firstElsif.getCondition)
          ifFromNewBody.getThenBlock.getStatements.head.replaceWithBlock(firstElsif.getBody)

          restOfElsifs.foreach(ifFromNewBody.addElsif(_))

          sourceElement.elseBlock.foreach { elseBlock =>
            ifFromNewBody.addElse(elseBlock)
          }

          Some(newBody)
        case Nil =>
          sourceElement
            .elseBlock
            .map(_.getBody)
      }
    }

    def isUsedAsExpression: Boolean = sourceElement.getParent match {
      case compoundStatement: RCompoundStatement =>
        compoundStatement.getLastChild == sourceElement && !compoundStatement.getParent.isInstanceOf[RFile]
      case _ => true
    }
  }

  implicit class IfStatementExtension
  (sourceElement: RIfStatement) extends IfOrUnlessStatementExtension(sourceElement.asInstanceOf[IfOrUnlessStatement]) {
    def addElsif(elsifToAdd: RElsifBlock)(implicit project: Project): PsiElement = {
      val newElsif = Parser.parseHeredoc(
        """
          |if CONDITION
          |  BODY
          |elsif CONDITION2
          |  ELSIF_BODY
          |end
        """).childOfType[RElsifBlock]()

      newElsif.getBody.getStatements.head.replaceWithBlock(elsifToAdd.getBody)
      newElsif.getCondition.replace(elsifToAdd.getCondition)
      sourceElement.addBefore(newElsif, sourceElement.getLastChild)
    }

    def addElse(elseToAdd: RElseBlock)(implicit project: Project): PsiElement = {
      val newElse = Parser.parseHeredoc(
        """
          |if CONDITION
          |  BODY
          |else
          |  ELSE_BODY
          |end
        """).childOfType[RElseBlock]()

      newElse.getBody.replace(elseToAdd.getBody)
      sourceElement.addBefore(newElse, sourceElement.getLastChild)
    }
  }

  implicit class MessageSendExtension(sourceElement: RCall) extends RPsiElementExtension(sourceElement) {
    def lastArgument: Option[RPsiElement] = {
      val arguments = sourceElement.getCallArguments.getElements
      arguments.lastOption.flatMap {
        case _: RArgumentToBlock => arguments.dropRight(1).lastOption
        case x => Some(x)
      }
    }

    def isRaise: Boolean = RubyCallTypes.isRaiseCall(sourceElement)
  }

  implicit class StringLiteralExtension(sourceElement: RStringLiteral) extends RPsiElementExtension(sourceElement) {
    def isDoubleQuoted: Boolean = {
      stringBeginningElementType.contains(RubyTokenTypes.tDOUBLE_QUOTED_STRING_BEG)
    }

    def isSingleQuoted: Boolean = {
      stringBeginningElementType.contains(RubyTokenTypes.tSINGLE_QUOTED_STRING_BEG)
    }

    private def stringBeginningElementType = {
      Option(sourceElement.getStringBeginning).map(_.getNode.getElementType)
    }
  }

  implicit class SymbolExtension(sourceElement: RSymbol) extends RPsiElementExtension(sourceElement) {
    def hasExpressionSubstitutions: Boolean = sourceElement.getContent match {
      case stringLiteral: RStringLiteral => stringLiteral.hasExpressionSubstitutions
      case _ => false
    }
  }

  implicit class PossibleCallExtension(sourceElement: RPossibleCall) extends RPsiElementExtension(sourceElement) {
    def isMessageSendWithImplicitReceiver: Boolean = {
      sourceElement.isCall &&
        (
          !sourceElement.getParent.isInstanceOf[RDotReference] ||
            sourceElement.getParent.asInstanceOf[RDotReference].getReceiver == sourceElement
          )
    }
  }

  implicit class IdentifierExtension(sourceElement: RIdentifier) extends PossibleCallExtension(sourceElement) {
    def firstDeclaration: RPsiElement = {
      ScopeUtil.getScope(sourceElement)
        .getDeclaredVariable(RubyPsiUtil.getRealContext(sourceElement), sourceElement.getText)
        .getFirstDeclaration
    }
  }

  implicit class BlockCallExtension(sourceElement: RBlockCall) extends RPsiElementExtension(sourceElement) {
    def delimiters: (String, String) = sourceElement match {
      case _: RBraceBlockCall => ("{", "}")
      case _ => ("do", "end")
    }
  }

  implicit class CodeBlockExtension(sourceElement: RCodeBlock) extends RPsiElementExtension(sourceElement) {
    def removeParametersBlock(): Unit = {
      val blockArguments = sourceElement.getBlockArguments
      sourceElement.deleteChildRange(
        blockArguments.getPrevSibling, // Ensures we remove the | delimiters
        blockArguments.getNextSibling
      )
    }

    def reformatParametersBlock()(implicit project: Project): Unit = {
      val blockArguments = sourceElement.getBlockArguments
      // FIXME: RUBY-REFACTORINGS-B - Assertion failed in reformatRange (sometimes sourceElement.isValid() is false!)
      CodeStyleManager.getInstance(project)
        .reformatRange(
          sourceElement,
          blockArguments.getPrevSibling.getTextRange.getStartOffset, // Ensures we include the | delimiters
          blockArguments.getNextSibling.getTextRange.getEndOffset,
          true
        )
    }


    def addParameter(parameterName: String): Unit = {
      sourceElement.getBlockArguments
        .addParameter(parameterName, ArgumentInfo.Type.SIMPLE, false)
    }
  }

  implicit class MethodExtension(sourceElement: RMethod) extends RPsiElementExtension(sourceElement) {
    lazy val body: RBodyStatement = sourceElement.childOfType[RBodyStatement]()

    def parameterIdentifiers: List[RIdentifier] = sourceElement.getArguments.map(_.getIdentifier).toList

    def hasParameters: Boolean = sourceElement.getArguments.nonEmpty

    def hasBlockParameter: Boolean = blockParameterInfo.isDefined

    def blockParameterName: Option[String] = blockParameterInfo.map(_.getName)

    def blockParameterInfo: Option[ArgumentInfo] = {
      sourceElement
        .getArgumentInfos
        .find(info => ArgumentInfo.Type.BLOCK.equals(info.getType))
    }

    def addBlockParameter(blockName: String): Int = {
      require(!hasBlockParameter, "The method already has a block parameter")

      sourceElement.getArgumentList.addParameter(s"&${blockName}", ArgumentInfo.Type.BLOCK, true)
    }

    def usesImplicitBlock: Boolean = {
      var usesImplicitBlock = false
      sourceElement.accept(new RubyRecursiveElementVisitor() {
        override def visitRYieldStatement(rYieldStatement: RYieldStatement): Unit = {
          super.visitRYieldStatement(rYieldStatement)
          usesImplicitBlock = true
        }

        override def visitRFid(rFid: RFid): Unit = {
          super.visitRFid(rFid)
          if (!rFid.isMessageSendWithImplicitReceiver) return

          if (rFid.textMatches("block_given?") || rFid.textMatches("iterator?")) {
            usesImplicitBlock = true
          }
        }
      })
      usesImplicitBlock
    }

    def isPublic: Boolean = Visibility.PUBLIC == sourceElement.getVisibility

    def replaceBodyWith(newBody: RCompoundStatement): RCompoundStatement = {
      val mainBody = body.getCompoundStatement
      val restOfBody = body.getChildren.filterNot(_.equals(mainBody))
      if (restOfBody.nonEmpty) {
        body.deleteChildRange(restOfBody.head, restOfBody.last)
        mainBody.getNextSibling.delete() // Removes extra newline left after deleting restOfBody
      }

      mainBody.replace(newBody).asInstanceOf[RCompoundStatement]
    }
  }

  implicit class CompoundStatementExtension(sourceElement: RCompoundStatement) extends RPsiElementExtension(sourceElement) {
    def replaceStatementsWithRange(startElement: PsiElement, endElement: PsiElement): Unit = {
      val originalFirstChild = sourceElement.getFirstChild
      val originalLastChild = sourceElement.getLastChild
      sourceElement.addRange(startElement, endElement)
      sourceElement.deleteChildRange(
        originalFirstChild,
        originalLastChild,
      )

      // We have to do this because a new line is added automatically
      sourceElement.getFirstChild match {
        case whiteSpace: PsiWhiteSpace => whiteSpace.delete()
      }
    }

    def asExpression(implicit project: Project): RPsiElement = {
      if (containsComments) {
        Parser.beginEndBlockWith(sourceElement)
      } else if (statements.isEmpty) {
        Parser.nil
      } else if (statements.length == 1) {
        statements.last
      } else {
        Parser.beginEndBlockWith(sourceElement)
      }
    }

    def containsComments: Boolean = findChildOfType[PsiComment](treeHeightLimit = 1).isDefined

    def statements: util.List[RPsiElement] = sourceElement.getStatements
  }

  implicit class WordsExtension(sourceElement: RWords) extends RPsiElementExtension(sourceElement) {
    def isSymbolList: Boolean = sourceElement.getFirstChild match {
      case Leaf(Ruby19TokenTypes.tSYMBOLS_BEG) => true
      case _ => false
    }

    // We parse the contents of the words element by hand, because the JetBrains parser does not
    // separate well the escaped characters...
    private object EscapableCharacter {
      def unapply(character: Char): Option[Char] = character match {
        case '(' | ')' | '\\' | Space(_) => Some(character)
        case _ => None
      }
    }

    private object Space {
      def unapply(character: Char): Option[Char] = character match {
        case ' ' | '\n' | '\t' => Some(character)
        case _ => None
      }
    }

    @tailrec
    private final def firstWordIn(text: List[Char], currentWord: String = ""): (String, List[Char]) = {
      text match {
        case Space(_) :: _ | Nil =>
          (currentWord, text)
        case '\\' :: EscapableCharacter(c) :: cs =>
          firstWordIn(cs, currentWord + c)
        case c :: cs =>
          firstWordIn(cs, currentWord + c)
      }
    }

    @tailrec
    private final def firstDelimiterIn(text: List[Char], currentDelimiter: String = ""): (String, List[Char]) = {
      text match {
        case Space(s) :: cs =>
          firstDelimiterIn(cs, currentDelimiter + s)
        case _ =>
          (currentDelimiter, text)
      }
    }

    @tailrec
    final def wordsIn(
      remainingCharacters: List[Char], currentWords: Vector[String] = Vector()
    ): Vector[String] = {
      if (remainingCharacters.isEmpty) return currentWords

      val (_, restAfterDelimiter) = firstDelimiterIn(remainingCharacters)
      val (word, restAfterWord) = firstWordIn(restAfterDelimiter)

      if (word.isEmpty) return currentWords

      wordsIn(restAfterWord, currentWords :+ word)
    }

    @tailrec
    final def delimitersIn(
      remainingCharacters: List[Char], currentDelimiters: Vector[String] = Vector()
    ): Vector[String] = {
      val (delimiter, restAfterDelimiter) = firstDelimiterIn(remainingCharacters)
      val (word, restAfterWord) = firstWordIn(restAfterDelimiter)

      if (word.isEmpty) return currentDelimiters :+ delimiter

      delimitersIn(restAfterWord, currentDelimiters :+ delimiter)
    }

    def values: List[String] = wordsIn(textContent).toList

    def wordSeparators: List[String] = delimitersIn(textContent).toList

    private lazy val textContent = sourceElement.getPsiContent
      .drop(1).dropRight(1)
      .map(_.getText)
      .mkString("")
      .toList
  }

  implicit class EditorExtension(editor: Editor) {
    def getSelectionStart: Int = editor.getSelectionModel.getSelectionStart

    def getSelectionEnd: Int = editor.getSelectionModel.getSelectionEnd

    def hasSelection: Boolean = editor.getSelectionModel.hasSelection

    def getSelectedText: String = {
      Option(editor.getSelectionModel.getSelectedText).getOrElse("")
    }

    def replaceSelectionWith(newText: String): Unit = {
      editor.getDocument.replaceString(
        this.getSelectionStart,
        this.getSelectionEnd,
        newText
      )
      editor.getCaretModel.getCurrentCaret.removeSelection()
    }

    def getCaretOffset: Int = {
      editor.getCaretModel.getCurrentCaret.getOffset
    }

    def moveCaretTo(targetOffset: Int): Unit = {
      editor.getCaretModel.getCurrentCaret.moveToOffset(targetOffset)
    }

    def scrollTo(targetOffset: Int, onScrollingFinished: => Unit = ()): Unit = {
      val scrollingModel = editor.getScrollingModel
      scrollingModel.scrollTo(
        editor.offsetToLogicalPosition(targetOffset),
        ScrollType.MAKE_VISIBLE
      )
      scrollingModel.runActionOnScrollingFinished(() => onScrollingFinished)
    }

    def rangeMarkerFor(psiElement: PsiElement): RangeMarker =
      editor.getDocument.rangeMarkerFor(psiElement)

    def rangeMarkerFor(textRange: TextRange): RangeMarker =
      editor.getDocument.rangeMarkerFor(textRange)

    def commitDocumentAndReindent(element: PsiElement)(implicit currentProject: Project): Unit = {
      val documentManager = PsiDocumentManager.getInstance(currentProject)
      val document = editor.getDocument
      documentManager.doPostponedOperationsAndUnblockDocument(document)
      documentManager.commitDocument(document)
      element.reindent()
    }
  }

  implicit class RangeMarkerExtension(rangeMarker: RangeMarker) {
    def getText: String = rangeMarker
      .getDocument
      .getCharsSequence
      .subSequence(rangeMarker.getStartOffset, rangeMarker.getEndOffset)
      .toString
  }

  implicit class TextRangeExtension(textRange: TextRange) {
    def shrinkLeft(amount: Int): TextRange = {
      require(amount < textRange.getLength)

      textRange.grown(-amount).shiftRight(amount)
    }
  }

  implicit class DocumentExtension(document: Document) {
    def rangeMarkerFor(psiElement: PsiElement): RangeMarker =
      rangeMarkerFor(psiElement.getTextRange)

    def rangeMarkerFor(textRange: TextRange): RangeMarker =
      document.createRangeMarker(textRange)

    def deleteStringIn(rangeMarker: RangeMarker): Unit = {
      document.deleteString(rangeMarker.getStartOffset, rangeMarker.getEndOffset)
    }

    def forceReparse(rangeStart: Int, rangeEnd: Int)(implicit project: Project): Unit = {
      val currentText = document.getText(new TextRange(rangeStart, rangeEnd))
      val documentManager = PsiDocumentManager.getInstance(project)

      document.replaceString(rangeStart, rangeEnd, "")
      documentManager.commitDocument(document)

      document.insertString(rangeStart, currentText)
      documentManager.commitDocument(document)
    }
  }

  implicit class PsiListExtension(source: Seq[PsiElement]) {
    def textRange: TextRange = {
      val textRanges = source.map(_.getTextRange)

      new TextRange(
        textRanges.map(_.getStartOffset).min,
        textRanges.map(_.getEndOffset).max
      )
    }
  }
}
