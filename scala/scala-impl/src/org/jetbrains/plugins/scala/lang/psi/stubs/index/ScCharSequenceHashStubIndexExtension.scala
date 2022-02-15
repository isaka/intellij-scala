package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{CharSequenceHashStubIndexExtension, StubIndex, StubIndexKey}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.reflect.ClassTag

/**
 * @see [[com.intellij.psi.stubs.CharSequenceHashStubIndexExtension#getKeyDescriptor()]]
 * @see [[com.intellij.psi.stubs.CharSequenceHashInlineKeyDescriptor]]
 */
abstract class ScCharSequenceHashStubIndexExtension[Psi <: PsiElement : ClassTag]
  extends CharSequenceHashStubIndexExtension[Psi] {

  protected def preprocessKey(key: CharSequence): CharSequence

  final def elementsByHash(key: CharSequence, project: Project, scope: GlobalSearchScope): Iterable[Psi] = {
    import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.StubIndexKeyExt
    val indexKey: StubIndexKey[CharSequence, Psi] = getKey
    val keyPreprocessed = preprocessKey(key)
    val result = indexKey.elements(keyPreprocessed, scope)(project)
    result
  }

  final def containsSuitableElement(
    key: CharSequence,
    project: Project,
    scope: GlobalSearchScope,
    requiredClass: Class[Psi],
    isSuitable: Psi => Boolean
  ): Boolean = {
    val indexKey: StubIndexKey[CharSequence, Psi] = getKey
    val keyPreprocessed: CharSequence = preprocessKey(key)

    val noSuitableElements = StubIndex.getInstance.processElements(
      indexKey,
      keyPreprocessed,
      project,
      scope,
      requiredClass,
      (t: Psi) => {
        val suitable = isSuitable(t)
        val continueProcessing = !suitable
        continueProcessing
      }
    )
    !noSuitableElements
  }
}

abstract class ScFqnHashStubIndexExtension[Psi <: PsiElement : ClassTag] extends ScCharSequenceHashStubIndexExtension[Psi] {
  override protected def preprocessKey(fqn: CharSequence): CharSequence =
    ScalaNamesUtil.cleanFqn(fqn.toString)
}