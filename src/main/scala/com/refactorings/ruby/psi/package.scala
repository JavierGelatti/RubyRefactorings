package com.refactorings.ruby

import com.intellij.openapi.editor.{Document, Editor, RangeMarker, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiWhiteSpace}
import com.refactorings.ruby.psi.Matchers.{EndOfLine, EscapeSequence, Leaf}
import com.refactorings.ruby.psi.Parser.parse
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.scope.ScopeUtil
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.lexer.ruby19.Ruby19TokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.{RStringLiteral, RWords}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures._
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.{RBodyStatement, RCompoundStatement, RElseBlock, RElsifBlock}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.{ArgumentInfo, RMethod, Visibility}
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RCodeBlock
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.{RArgumentToBlock, RCall, RubyCallTypes}
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RDotReference
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.fields.{RClassVariable, RInstanceVariable}
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.{RFid, RIdentifier, RPseudoConstant}
import org.jetbrains.plugins.ruby.ruby.lang.psi.visitors.RubyRecursiveElementVisitor
import org.jetbrains.plugins.ruby.ruby.lang.psi.{RPossibleCall, RPsiElement, RubyPsiUtil}

import scala.PartialFunction.cond
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
        case otherElement => otherElement.findParentOfType[T](treeHeightLimit - 1)
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

      sourceElement.getChildren
        .find(
          element => tag.runtimeClass.isInstance(element) && matching(element.asInstanceOf[T])
        )
        .map(_.asInstanceOf[T])
        .orElse({
          sourceElement.getChildren
            .view
            .flatMap(directChild => directChild.findChildOfType[T](treeHeightLimit - 1, matching))
            .headOption
        })
    }

    def replaceWithBlock(elementsToReplaceBodyWith: RCompoundStatement): Unit = {
      val statements = elementsToReplaceBodyWith.getStatements
      sourceElement.getParent.addRangeBefore(
        statements.head,
        statements.last,
        sourceElement
      )

      val extraNewLine = sourceElement.getPrevSibling
      sourceElement.delete()
      extraNewLine.delete()
    }

    def putBefore(referenceElement: RPsiElement): ElementType = {
      referenceElement.getParent.addBefore(sourceElement, referenceElement).asInstanceOf[ElementType]
    }

    def putAfter(referenceElement: RPsiElement): ElementType = {
      referenceElement.getParent.addAfter(sourceElement, referenceElement).asInstanceOf[ElementType]
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
  }

  type IfOrUnlessStatement = RExpression with RBlockStatement with RConditionalStatement {
    def getThenBlock: RCompoundStatement
    def getElseBlock: RElseBlock
  }

  implicit class IfOrUnlessStatementExtension
  (sourceElement: IfOrUnlessStatement) extends PsiElementExtension(sourceElement) {
    def hasNoAlternativePaths: Boolean = hasNoElseBlock && hasNoElsifBlocks

    def hasAlternativePaths: Boolean = !hasNoAlternativePaths

    def hasNoElseBlock: Boolean = sourceElement.getElseBlock == null

    def hasNoElsifBlocks: Boolean = getElsifBlocks.isEmpty

    def hasEmptyThenBlock: Boolean = sourceElement.getThenBlock.getStatements.isEmpty

    def negatedKeyword: String = sourceElement match {
      case _: RIfStatement => "unless"
      case _: RUnlessStatement => "if"
    }

    def getElsifBlocks: List[RElsifBlock] = {
      sourceElement match {
        case ifStatement: RIfStatement => ifStatement.getElsifBlocks.toList
        case _: RUnlessStatement => Nil
      }
    }

    def elseBlock: Option[RElseBlock] = Option(sourceElement.getElseBlock)
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

  implicit class MessageSendExtension(sourceElement: RCall) extends PsiElementExtension(sourceElement) {
    def lastArgument: Option[RPsiElement] = {
      val arguments = sourceElement.getCallArguments.getElements
      arguments.lastOption.flatMap {
        case _: RArgumentToBlock => arguments.dropRight(1).lastOption
        case x => Some(x)
      }
    }

    def isRaise: Boolean = RubyCallTypes.isRaiseCall(sourceElement)
  }

  implicit class StringLiteralExtension(sourceElement: RStringLiteral) extends PsiElementExtension(sourceElement) {
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

  implicit class PossibleCallExtension(sourceElement: RPossibleCall) extends PsiElementExtension(sourceElement) {
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

  implicit class CodeBlockExtension(sourceElement: RCodeBlock) extends PsiElementExtension(sourceElement) {
    def removeParametersBlock(): Unit = {
      val blockArguments = sourceElement.getBlockArguments
      sourceElement.deleteChildRange(
        blockArguments.getPrevSibling, // Ensures we remove the | delimiters
        blockArguments.getNextSibling
      )
    }

    def addParameter(parameterName: String): Unit = {
      sourceElement.getBlockArguments
        .addParameter(parameterName, ArgumentInfo.Type.SIMPLE, false)
    }
  }

  implicit class MethodExtension(sourceElement: RMethod) extends PsiElementExtension(sourceElement) {
    def parameterIdentifiers: List[RIdentifier] = sourceElement.getArguments.map(_.getIdentifier).toList

    def body: RBodyStatement = sourceElement.childOfType[RBodyStatement]()

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
      body.getNextSibling match {
        case EndOfLine(_) => ()
        case _ => newBody.add(Parser.endOfLine)
      }

      val restOfBody = body.getChildren.filterNot(_.equals(mainBody))
      if (restOfBody.nonEmpty) {
        body.deleteChildRange(restOfBody.head, restOfBody.last)
      }

      mainBody.replace(newBody).asInstanceOf[RCompoundStatement]
    }

    /**
     * Sometimes this is necessary because the Ruby parser from org.jetbrains.plugins.ruby does not always use the same
     * PsiElement type to represent new lines in the code. The parser can produce either a PsiWhiteSpace('\n') or a
     * PsiElement(end of line).
     *
     * This should be an implementation detail that we shouldn't need to care about. However, sometimes the formatter
     * behaves differently if it receives either a PsiWhiteSpace with newlines or a PsiElement(end of line).
     *
     * In particular, this affects the code modification results when trying to edit methods. This happens because:
     * - When a method has its parameter declarations between parentheses, the parser produces a
     *   "Function argument list" followed by a PsiWhiteSpace('\n').
     * - When a method has its parameter declarations without parentheses, the parser produces a "Command argument list"
     *   followed by a PsiElement(end of line).
     * - After performing a change, the formatter correctly fixes the indentation when there's a PsiWhiteSpace after the
     *   method arguments, but it does not when there's a PsiElement(end of line).
     *
     * To overcome this problem, we normalize the spaces after the parameter list so that they're always represented by
     * a PsiWhiteSpace. In this way, the formatter works fine after performing changes in the PsiElements.
     */
    def normalizeSpacesAfterParameterList()(implicit project: Project): Unit = {
      val argumentList = sourceElement.getArgumentList

      (argumentList.getNextSibling, argumentList.getNextSibling.getNextSibling) match {
        case (EndOfLine(eol), space: PsiWhiteSpace) =>
          val endOfLineAndWhitespace = parse(s"\n${space.getText}")

          // Ordering here matters: swapping these two lines causes a PsiInvalidElementAccessException
          space.replace(endOfLineAndWhitespace)
          eol.delete()
        case _ => ()
      }
    }
  }

  implicit class CompoundStatementExtension(sourceElement: RCompoundStatement) extends PsiElementExtension(sourceElement) {
    def replaceStatementsWithRange(startElement: PsiElement, endElement: PsiElement): Unit = {
      val originalStatements = sourceElement.getStatements
      sourceElement.addRange(startElement, endElement)
      originalStatements.forEach(_.delete())
    }
  }

  implicit class WordsExtension(sourceElement: RWords) extends PsiElementExtension(sourceElement) {
    def isSymbolList: Boolean = sourceElement.getFirstChild match {
      case Leaf(Ruby19TokenTypes.tSYMBOLS_BEG) => true
      case _ => false
    }

    def values: List[String] = {
      val words = new ListBuffer[String]
      var currentWord = ""

      contentWithoutDelimiters.foreach {
        case EscapeSequence(escape) =>
          currentWord += escape.getText.stripPrefix("\\")

        case wordsContent =>
          splitWordsUnescapingSpaces(wordsContent.getText) match {
            case Nil | List("") =>
              words.addOne(currentWord)
              currentWord = ""

            case List(singleWord) =>
              currentWord += singleWord

            case firstWord +: middleWords :+ lastWord =>
              words.addOne(currentWord + firstWord)
              words.addAll(middleWords)
              currentWord = lastWord
          }
      }
      words.addOne(currentWord)

      words.filterNot(_.isEmpty).toList
    }

    def wordSeparators: List[String] = {
      val separators = new ListBuffer[String]

      contentWithoutDelimiters.foreach {
        case EscapeSequence(_) => ()

        case wordsContent => separators.addAll(
          wordsSeparatorRegex.r.findAllIn(wordsContent.getText)
        )
      }

      contentWithoutDelimiters match {
        case Nil => separators.addOne("")

        case _ =>
          if (missingStartDelimiter(contentWithoutDelimiters.head))
            separators.prepend("")
          if (missingEndDelimiter(contentWithoutDelimiters.last))
            separators.append("")
      }

      separators.toList
    }

    private def missingStartDelimiter(firstWordElement: PsiElement) = {
      val missingStartDelimiter = firstWordElement match {
        case EscapeSequence(_) => true

        case wordsContent => !wordsContent.getText.matches("(?s)^" + wordsSeparatorRegex + ".*")
      }
      missingStartDelimiter
    }

    private def missingEndDelimiter(lastWordElement: PsiElement) = {
      val missingStartDelimiter = lastWordElement match {
        case EscapeSequence(_) => true

        case wordsContent => !wordsContent.getText.matches("(?s).*" + wordsSeparatorRegex + "$")
      }
      missingStartDelimiter
    }

    private lazy val contentWithoutDelimiters = {
      sourceElement.getPsiContent.drop(1).dropRight(1).toList
    }

    private def splitWordsUnescapingSpaces(text: String) = {
      // This is needed because the parser doesn't parse a \<space> as an escape sequence.
      val endsWord = text.matches(".*" + wordsSeparatorRegex + "$")
      val words = text
        .split(wordsSeparatorRegex)
        .map(_.replaceAll("\\\\(\\s+)", "$1"))
        .toList

      if (endsWord) words.appended("") else words
    }

    private lazy val wordsSeparatorRegex = "(?<!\\\\)\\s+"
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
