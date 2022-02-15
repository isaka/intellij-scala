package org.jetbrains.plugins.scala.annotator

import com.intellij.openapi.module.JavaModuleType
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.junit.Assert.assertEquals

//see description of SCL-19969
class PackagesWithSameHashCodeTest extends ScalaHighlightingTestBase {

  private val module1Src = "module1/src"
  private val module2Src = "module2/src"

  override protected def setUp(): Unit = {
    super.setUp()

    val module1 = PsiTestUtil.addModule(getProject, JavaModuleType.getModuleType, "module1", myFixture.getTempDirFixture.findOrCreateDir("module1"))
    val module2 = PsiTestUtil.addModule(getProject, JavaModuleType.getModuleType, "module2", myFixture.getTempDirFixture.findOrCreateDir("module2"))

    PsiTestUtil.addSourceRoot(module1, myFixture.getTempDirFixture.findOrCreateDir(module1Src))
    PsiTestUtil.addSourceRoot(module2, myFixture.getTempDirFixture.findOrCreateDir(module2Src))
  }

  def testPackagesWithSameHashCodeShouldBeHandledCorrectly(): Unit = {
    //see description of SCL-19969
    val package1InModule1 = "prefix1_Aa"
    val package1InModule2 = "prefix1_BB"
    assertEquals(package1InModule1.hashCode, package1InModule2.hashCode)

    val package2InModule1 = "prefix2/Aa"
    val package2InModule2 = "prefix2/BB"
    assertEquals(package2InModule1.hashCode, package2InModule2.hashCode)

    //module 1
    myFixture.addFileToProject(s"$module1Src/package_in_module1/ClassInModule1.scala", "package package_in_module1\nclass ClassInModule1")
    myFixture.addFileToProject(s"$module1Src/$package1InModule1/Class1InModule1.scala", "package prefix1_Aa\nclass Class1InModule1")
    myFixture.addFileToProject(s"$module1Src/$package2InModule1/Class2InModule1.scala", "package prefix2.Aa\nclass Class2InModule1")

    val file1 = myFixture.addFileToProject(s"$module1Src/$package1InModule1/Usage1.scala",
      """package prefix1_Aa
        |
        |class Usage1 {
        |  var _: prefix1_BB.Unknown = _
        |  var _: prefix1_BB.Class1InModule2 = _
        |  var _: package_in_module2.Unknown = _
        |  var _: package_in_module2.ClassInModule2 = _
        |
        |  var _: prefix1_Aa.Class1InModule1 = _
        |  var _: package_in_module1.ClassInModule1 = _
        |}
        |""".stripMargin
    ).asInstanceOf[ScalaFile]

    val file2 = myFixture.addFileToProject(s"$module1Src/$package1InModule2/Usage2.scala",
      """package prefix2.Aa
        |
        |class Usage2 {
        |  var _: prefix2.BB.Unknown = _
        |  var _: prefix2.BB.Class1InModule2 = _
        |
        |  var _: prefix2.Aa.Class2InModule1 = _
        |}
        |""".stripMargin
    ).asInstanceOf[ScalaFile]

    //module 2
    myFixture.addFileToProject(s"$module2Src/package_in_module2/ClassInModule2.scala", "package package_in_module2\nclass ClassInModule2")
    myFixture.addFileToProject(s"$module2Src/$package1InModule2/Class1InModule2.scala", "package prefix1_BB\nclass Class1InModule2")
    myFixture.addFileToProject(s"$module2Src/$package2InModule2/Class2InModule2.scala", "package prefix2.BB\nclass Class2InModule2")


    //NOTE: real editor contains twice less errors because (looks like) it only shows first error found inside element children
    assertErrorsText(
      file1,
      """Error(Unknown,Cannot resolve symbol Unknown)
        |Error(prefix1_BB,Cannot resolve symbol prefix1_BB)
        |
        |Error(Class1InModule2,Cannot resolve symbol Class1InModule2)
        |Error(prefix1_BB,Cannot resolve symbol prefix1_BB)
        |
        |Error(Unknown,Cannot resolve symbol Unknown)
        |Error(package_in_module2,Cannot resolve symbol package_in_module2)
        |
        |Error(ClassInModule2,Cannot resolve symbol ClassInModule2)
        |Error(package_in_module2,Cannot resolve symbol package_in_module2)
        |""".stripMargin
    )
    assertErrorsText(
      file2,
      """Error(Unknown,Cannot resolve symbol Unknown)
        |Error(BB,Cannot resolve symbol BB)
        |
        |Error(Class1InModule2,Cannot resolve symbol Class1InModule2)
        |Error(BB,Cannot resolve symbol BB)""".stripMargin
    )
  }
}
