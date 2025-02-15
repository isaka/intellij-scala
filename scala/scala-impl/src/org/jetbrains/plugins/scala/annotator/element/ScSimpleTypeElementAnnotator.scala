package org.jetbrains.plugins.scala
package annotator
package element

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.{ResolvesTo, childOf}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScParameterizedTypeElement, ScSimpleTypeElement, ScTypeArgs, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationsHolder, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScGenericCall, ScTypedExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

object ScSimpleTypeElementAnnotator extends ElementAnnotator[ScSimpleTypeElement] {

  // TODO Shouldn't the ScExpressionAnnotator be enough?
  override def annotate(element: ScSimpleTypeElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    //todo: check bounds conformance for parameterized type
    checkAbsentTypeArgs(element)
  }

  private def checkAbsentTypeArgs(typeElement: ScSimpleTypeElement)
                                 (implicit holder: ScalaAnnotationHolder): Unit = {
    // Dirty hack(see SCL-12582): we shouldn't complain about missing type args since they will be added by a macro after expansion
    def isFreestyleAnnotated(ah: ScAnnotationsHolder): Boolean = {
      (ah.findAnnotationNoAliases("freestyle.free") != null) ||
        ah.findAnnotationNoAliases("freestyle.module") != null
    }

    def needTypeArgs: Boolean = {
      def noHigherKinds(owner: ScTypeParametersOwner) = !owner.typeParameters.exists(_.typeParameters.nonEmpty)

      val typeElementResolved = typeElement.reference.map(_.resolve()) match {
        case Some(r) => r
        case _ =>
          return false
      }

      val canHaveTypeArgs = typeElementResolved match {
        case ah: ScAnnotationsHolder if isFreestyleAnnotated(ah) => false
        case c: PsiClass                                         => c.hasTypeParameters
        case owner: ScTypeParametersOwner                        => owner.typeParameters.nonEmpty
        case _                                                   => false
      }

      if (!canHaveTypeArgs)
        return false

      typeElement.getParent match {
        case ScParameterizedTypeElement(`typeElement`, _)                        => false
        case tp: ScTypeParam if tp.contextBoundTypeElement.contains(typeElement) => false
        case (_: ScTypeArgs) childOf (gc: ScGenericCall) =>
          gc.referencedExpr match {
            case ResolvesTo(f: ScFunction) => noHigherKinds(f)
            case _                         => false
          }
        case (_: ScTypeArgs) childOf (parameterized: ScParameterizedTypeElement) =>
          parameterized.typeElement match {
            case ScSimpleTypeElement(ResolvesTo(target)) =>
              target match {
                case ScPrimaryConstructor.ofClass(c) => noHigherKinds(c)
                case owner: ScTypeParametersOwner    => noHigherKinds(owner)
                case _                               => false
              }
            case _ => false
          }
        case infix: ScInfixTypeElement if infix.left == typeElement || infix.rightOption.contains(typeElement) =>
          infix.operation.resolve() match {
            case owner: ScTypeParametersOwner => noHigherKinds(owner)
            case _                            => false
          }
        case _ =>
          //SCL-19477, this code is OK, no need in type argument
          //def f[T]: "42" = ???
          //val refOk: f.type = ???
          typeElementResolved match {
            case f: ScFunction => !f.isStable
            case _             => true
          }
      }
    }

    val needTypeArgsRes = needTypeArgs
    if (needTypeArgsRes) {
      holder.createErrorAnnotation(
        typeElement,
        ScalaBundle.message("type.takes.type.parameters", typeElement.getText),
        ProblemHighlightType.GENERIC_ERROR
      )
    }
  }
}
