package com.refactorings.ruby

import com.intellij.openapi.util.TextRange

class CannotApplyRefactoringException(message: String, val textRange: TextRange) extends RuntimeException(message)
