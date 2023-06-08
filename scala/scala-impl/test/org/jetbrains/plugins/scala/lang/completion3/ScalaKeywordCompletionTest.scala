package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.runner.RunWith

class ScalaKeywordCompletionTest extends ScalaCompletionTestBase {

  def testPrivateVal(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  private va$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  private val $CARET
         |}
      """.stripMargin,
    item = "val"
  )

  def testPrivateThis(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  pr$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  private[$CARET]
         |}
      """.stripMargin,
    item = "private",
    char = '['
  )

  def testFirstVal(): Unit = doCompletionTest(
    fileText =
      s"""
         |class A {
         |  def foo() {
         |    va${CARET}vv.v
         |  }
         |}
      """.stripMargin,
    resultText =
      s"""
         |class A {
         |  def foo() {
         |    val ${CARET}vv.v
         |  }
         |}
      """.stripMargin,
    item = "val",
    char = ' '
  )

  def testIfAfterCase(): Unit = doCompletionTest(
    fileText =
      s"""
         |1 match {
         |  case a if$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case a if $CARET
         |}
      """.stripMargin,
    item = "if",
    char = ' '
  )

  def testValUnderCaseClause(): Unit = doCompletionTest(fileText =
    s"""
       |1 match {
       |  case 1 =>
       |    val$CARET
       |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case 1 =>
         |    val $CARET
         |}
      """.stripMargin,
    item = "val",
    char = ' '
  )

  def testDefUnderCaseClause(): Unit = doCompletionTest(
    fileText =
      s"""
         |1 match {
         |  case 1 =>
         |    def$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case 1 =>
         |    def $CARET
         |}
      """.stripMargin,
    item = "def",
    char = ' '
  )

  def testIfParentheses(): Unit = doCompletionTest(
    fileText =
      s"""
         |1 match {
         |  case 1 =>
         |    if$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case 1 =>
         |    if ($CARET)
         |}
      """.stripMargin,
    item = "if",
    char = '('
  )

  def testTryBraces(): Unit = doCompletionTest(
    fileText =
      s"""
         |1 match {
         |  case 1 =>
         |    try$CARET
         |}
      """.stripMargin,
    resultText =
      s"""
         |1 match {
         |  case 1 =>
         |    try {$CARET}
         |}
      """.stripMargin,
    item = "try",
    char = '{'
  )

  def testDoWhile(): Unit = doCompletionTest(
    fileText =
      s"""
         |do {} whi$CARET
         |1
      """.stripMargin,
    resultText =
      s"""
         |do {} while ($CARET)
         |1
      """.stripMargin,
    item = "while",
    char = '('
  )

  def testExtendsAsLastInFile(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test e$CARET
         |""".stripMargin,
    resultText =
      s"""
         |class Test extends $CARET
         |""".stripMargin,
    item = "extends"
  )

  def testExtendsBeforeSemicolon(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test e$CARET;
         |""".stripMargin,
    resultText =
      s"""
         |class Test extends $CARET;
         |""".stripMargin,
    item = "extends"
  )

  // SCL-19181
  def testExtendsBeforeId(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test e$CARET Base
         |""".stripMargin,
    resultText =
      s"""
         |class Test extends ${CARET}Base
         |""".stripMargin,
    item = "extends"
  )


  def testExtendsBetweenClasses(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test e$CARET
         |class Test2
         |""".stripMargin,
    resultText =
      s"""
         |class Test extends $CARET
         |class Test2
         |""".stripMargin,
    item = "extends"
  )

  // SCL-19022
  def testExtendsBeforeBody(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Test e$CARET {
         |}
         |""".stripMargin,
    resultText =
      s"""
         |class Test extends $CARET{
         |}
         |""".stripMargin,
    item = "extends"
  )

  def testExtendsBeforeObjectBody(): Unit = doCompletionTest(
    fileText =
      s"""
         |object Test e$CARET {
         |}
         |""".stripMargin,
    resultText =
      s"""
         |object Test extends $CARET{
         |}
         |""".stripMargin,
    item = "extends"
  )

  def testExtendsBeforeExtends(): Unit = checkNoBasicCompletion(
    fileText =
      s"""
         |object Obj e$CARET extends
         |""".stripMargin,
    item = "extends"
  )
}

/** Version specific tests */

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13
))
class ScalaKeywordCompletionTest_2_13 extends ScalaCompletionTestBase {
  def testMatch(): Unit = doCompletionTest(
    fileText =
      s"42 m$CARET",
    resultText =
      s"""42 match {
         |  case $CARET
         |}""".stripMargin,
    item = "match"
  )

  def testInfixMatch(): Unit = doCompletionTest(
    fileText =
      s"42 m$CARET ",
    resultText =
      s"""42 match {
         |  case $CARET
         |}""".stripMargin,
    item = "match"
  )

  def testCatch(): Unit = doCompletionTest(
    fileText =
      s"try 42 c$CARET",
    resultText =
      s"""try 42 catch {
         |  case $CARET
         |}""".stripMargin,
    item = "catch"
  )
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
class ScalaKeywordCompletionTest_3_Latest extends ScalaCompletionTestBase {
  def testMatch(): Unit = doCompletionTest(
    fileText =
      s"42 m$CARET",
    resultText =
      s"""42 match
         |  case $CARET""".stripMargin,
    item = "match"
  )

  def testInfixMatch(): Unit = doCompletionTest(
    fileText =
      s"42 m$CARET ",
    resultText =
      s"""42 match
         |  case $CARET""".stripMargin,
    item = "match"
  )

  def testCatch(): Unit = doCompletionTest(
    fileText =
      s"try 42 c$CARET",
    resultText =
      s"""try 42 catch
         |  case $CARET""".stripMargin,
    item = "catch"
  )
}
