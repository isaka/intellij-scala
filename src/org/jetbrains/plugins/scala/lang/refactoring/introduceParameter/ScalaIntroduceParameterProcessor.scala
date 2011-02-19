package org.jetbrains.plugins.scala.lang.refactoring.introduceParameter

import com.intellij.refactoring.BaseRefactoringProcessor
import java.lang.String
import com.intellij.openapi.project.Project
import com.intellij.psi.search.searches.{MethodReferencesSearch, OverridingMethodsSearch}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.usageView.{UsageViewUtil, UsageViewDescriptor, UsageInfo}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import com.intellij.openapi.util.TextRange
import collection.mutable.ArrayBuffer
import java.util.{Comparator, Arrays}
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi._
import gnu.trove.TIntArrayList
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.refactoring.introduceParameter.{IntroduceParameterMethodUsagesProcessor, IntroduceParameterData}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaIntroduceParameterProcessor(project: Project, editor: Editor, methodToSearchFor: PsiMethod,
                                       function: ScFunctionDefinition, replaceAllOccurences: Boolean,
                                       occurrences: Array[TextRange], startOffset: Int, endOffset: Int,
                                       paramName: String, isDefaultParam: Boolean, tp: ScType, expression: ScExpression)
        extends BaseRefactoringProcessor(project) with IntroduceParameterData {
  private val document = editor.getDocument
  private val file = function.getContainingFile
  private def getRangeElementOrFile(range: TextRange): PsiElement = {
    val startElement = file.findElementAt(range.getStartOffset)
    val endElement = file.findElementAt(range.getEndOffset - 1)
    var element = PsiTreeUtil.findCommonParent(startElement, endElement)
    while (element.getTextRange.getStartOffset >= range.getStartOffset &&
           element.getTextRange.getEndOffset   <= range.getEndOffset) {
      if (element.getTextRange.equals(range)) return element
      element = element.getParent
    }
    return file
  }

  private def changeMethodSignatureAndResolveFieldConflicts(usage: UsageInfo, usages: Array[UsageInfo]): Unit = {
    for (processor <- IntroduceParameterMethodUsagesProcessor.EP_NAME.getExtensions) {
      if (!processor.processChangeMethodSignature(this, usage, usages)) return
    }
  }

  private case class MethodUsageInfo(ref: PsiElement) extends UsageInfo(ref)
  private case class FileRangeUsageInfo(file: PsiFile, range: TextRange)
    extends UsageInfo(file, range.getStartOffset, range.getEndOffset)
  private case class ElementRangeUsageInfo(element: PsiElement, range: TextRange)
    extends UsageInfo(element)
  private case class IPUsageInfo(elem: PsiMethod) extends UsageInfo(elem)

  def getCommandName: String = "Introduce Parameter"

  def performRefactoring(usages: Array[UsageInfo]): Unit = {
    val sortedUsages = Arrays.copyOf(usages, usages.length)
    Arrays.sort(sortedUsages, new Comparator[UsageInfo] {
      def compare(o1: UsageInfo, o2: UsageInfo): Int =
        if (o1.startOffset != o2.startOffset) o1.startOffset - o2.startOffset
        else o1.endOffset - o2.endOffset
    })
    val iter = sortedUsages.reverseIterator
    while (iter.hasNext) {
      val usage = iter.next
      usage match {
        case IPUsageInfo(method) =>
          changeMethodSignatureAndResolveFieldConflicts(usage, usages)
        case MethodUsageInfo(ref) =>
        case ElementRangeUsageInfo(element, range) =>
          element match {
            case expr: ScExpression =>
              val refExpr = ScalaPsiElementFactory.createExpressionFromText(paramName, element.getManager)
              expr.replaceExpression(refExpr, true)
            case _ => document.replaceString(range.getStartOffset, range.getEndOffset, paramName)
          }
        case FileRangeUsageInfo(file, range) =>
          document.replaceString(range.getStartOffset, range.getEndOffset, paramName)

      }
    }
  }

  def findUsages: Array[UsageInfo] = {
    val result: ArrayBuffer[UsageInfo] = new ArrayBuffer[UsageInfo]
    val overridingMethods: Array[PsiMethod] =
      OverridingMethodsSearch.search(methodToSearchFor, methodToSearchFor.getUseScope, true).
        toArray(PsiMethod.EMPTY_ARRAY)
    result += IPUsageInfo(methodToSearchFor)
    for (overridingMethod <- overridingMethods) {
      result += IPUsageInfo(overridingMethod)
    }
    val refs: Array[PsiReference] =
      MethodReferencesSearch.search(methodToSearchFor, GlobalSearchScope.projectScope(myProject), true).
        toArray(PsiReference.EMPTY_ARRAY)
    for (refa <- refs; ref = refa.getElement) {
      result += MethodUsageInfo(ref)
    }
    if (replaceAllOccurences) {
      for (occurrence <- occurrences) {
        val element = getRangeElementOrFile(occurrence)
        element match {
          case file: PsiFile => result += FileRangeUsageInfo(file, occurrence)
          case _ => result += ElementRangeUsageInfo(element, occurrence)
        }
      }
    }
    else {
      val range = new TextRange(startOffset, endOffset)
      val element = getRangeElementOrFile(range)
      element match {
        case file: PsiFile => result += FileRangeUsageInfo(file, range)
        case _ => result += ElementRangeUsageInfo(element, range)
      }
    }
    val usageInfos: Array[UsageInfo] = result.toArray
    return UsageViewUtil.removeDuplicatedUsages(usageInfos)
  }

  def createUsageViewDescriptor(usages: Array[UsageInfo]): UsageViewDescriptor = {
    new ScalaIntroduceParameterViewDescriptor(methodToSearchFor)
  }

  def getParametersToRemove: TIntArrayList = new TIntArrayList() //todo:

  def getForcedType: PsiType = ScType.toPsi(tp, project, function.getResolveScope)

  def getScalaForcedType: ScType = tp

  def isGenerateDelegate: Boolean = false //todo:?

  def isDeclareFinal: Boolean = false //todo:?

  def isDeclareDefault: Boolean = isDefaultParam

  def getReplaceFieldsWithGetters: Int = 0 //todo:

  def isReplaceAllOccurences: Boolean = replaceAllOccurences

  def getParameterName: String = paramName

  def isRemoveLocalVariable: Boolean = false //todo:

  def getLocalVariable: PsiLocalVariable = null //todo:

  def getScalaExpressionToSearch: ScExpression = expression

  def getExpressionToSearch: PsiExpression = null //todo:

  def getParameterInitializer: PsiExpression = null //todo:

  def getMethodToSearchFor: PsiMethod = methodToSearchFor

  def getMethodToReplaceIn: PsiMethod = function

  def getProject: Project = project
}