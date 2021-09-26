package com.refactorings

import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.Strings
import com.intellij.util.ThrowableRunnable

import java.util
import scala.collection.mutable
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.language.implicitConversions

package object ruby {
  implicit def list2Scala[T]: util.List[T] => mutable.Buffer[T] = list => list.asScala
  implicit def fun2Runnable(fun: => Unit): Runnable = () => fun
  implicit def fun2ThrowableRunnable[E <: Throwable](fun: => Unit): ThrowableRunnable[E] = () => fun
  implicit def fun2ThrowableComputable[E <: Throwable, R](fun: => R): ThrowableComputable[R, E] = () => fun

  implicit class OptionExtension[T](source: Option[T]) {
    def mapIf[S >: T](f: PartialFunction[T, S]): Option[S] = {
      source.collect(f.orElse(x => x))
    }
  }

  implicit class StringExtension(source: String) {
    def snakeToPascalCase: String = source.split("_").map(_.capitalize).mkString
    def isNotEmptyOrSpaces: Boolean = !Strings.isEmptyOrSpaces(source)
  }

  implicit class ListExtension[Element](source: List[Element]) {
    def movingToStart(elementToMoveToStart: Element): List[Element] = {
      source.span(_ != elementToMoveToStart) match {
        case (prefix, foundElement::suffix) =>
          foundElement :: prefix ++ suffix
        case _ =>
          throw new IllegalArgumentException(s"${source} should contain ${elementToMoveToStart}")
      }
    }
  }
}
