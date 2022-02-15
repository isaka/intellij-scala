package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

final class ScPackageObjectFqnIndex extends ScFqnHashStubIndexExtension[ScObject] {

  override def getKey: StubIndexKey[CharSequence, ScObject] =
    ScalaIndexKeys.PACKAGE_OBJECT_FQN_KEY
}

object ScPackageObjectFqnIndex {
  def instance: ScPackageObjectFqnIndex = new ScPackageObjectFqnIndex
}
