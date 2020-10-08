package org.jetbrains.plugins.scala.lang.psi.cfg

import com.intellij.psi.{PsiMethod, PsiNamedElement}
import org.jetbrains.plugins.scala.dfa._
import org.jetbrains.plugins.scala.lang.psi.api.base.literals._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScParameterOwner}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

private trait ExpressionTransformer { this: Transformer =>
  final def transformExpression(expr: ScExpression): builder.Value = transformExpression(expr, ResultReq.Needed).value
  final def transformExpression(expr: ScExpression, rreq: ResultReq): rreq.Result[builder.Value] = attachSourceInfoIfSome(expr) {
    Some(expr match {
        // **************** Literals **************** //
      case ScBooleanLiteral(bool) => builder.constant(DfBool(bool))
      case ScCharLiteral(char) => builder.constant(transformationNotSupported)
      case ScIntegerLiteral(int) => builder.constant(DfInt(int))
      case ScLongLiteral(long) => builder.constant(transformationNotSupported)
      case ScFloatLiteral(float) => builder.constant(transformationNotSupported)
      case ScDoubleLiteral(double) => builder.constant(transformationNotSupported)
      case _: ScNullLiteral => builder.constant(DfNull.Always)
      case _: ScUnitExpr => builder.constant(DfUnit.Concrete)
      case ScSymbolLiteral(_) => transformationNotSupported
      case ScStringLiteral(str) => builder.constant(new DfStringRef(str))

      // ***************** Reference **************** //
      case reference: ScReferenceExpression => transformReference(reference)

      // ******************* Block ****************** //
      case ScParenthesisedExpr(inner) => transformExpression(inner)
      case block: ScBlockExpr => return transformStatements(block.statements, rreq)


      // *************** Control stuff *************** //
      case ScIf(condExpr, thenExpr, elseExpr) =>
        // if
        val condValue = condExpr.fold(builder.constant(DfBool.Top))(transformExpression)
        val elseOrEndJump = builder.jumpToFutureIfNot(condValue, afterBlockName = "then")
        val resultVariable = rreq.ifNeeded(builder.newVariable("ifResult", new AnyRef))

        def buildAndWriteIfNeeded(expr: Option[ScExpression], default: DfAny): Unit = {
          val maybeResult = expr.flatMap(transformExpression(_, rreq))
          for (resultVariable <- resultVariable) {
            val result = maybeResult.getOrElse(builder.constant(default))
            builder.writeVariable(resultVariable, result)
          }
        }

        // then
        buildAndWriteIfNeeded(thenExpr, DfNothing)

        // else
        val endJump = if (rreq.needed || elseExpr.isDefined) {
          val endJump = builder.jumpToFuture()
          builder.jumpHere("else", elseOrEndJump)
          buildAndWriteIfNeeded(elseExpr, DfUnit.Concrete)

          endJump
        } else elseOrEndJump

        // end
        builder.jumpHere("endIf", endJump)
        return rreq.map(resultVariable)(builder.readVariable)

      case _ => transformationNotSupported
    })
  }

  final def transformReference(reference: ScReferenceExpression): builder.Value = attachSourceInfo(reference) {
    reference.bind() match {
      case Some(result) if reference.refName != result.name && (result.name == "apply" || result.name == "update") =>
        builder.readVariable(variable(result.parentElement.get))

      case Some(ResolvesToFunction(func)) =>
        //InvocationInfo(this.qualifier, Some(func), Seq.empty)
        //  .build(rreq)
        transformationNotSupported

      case Some(result) =>
        builder.readVariable(variable(result.element))

      case None =>
        // we have to be able to handle the error here
        transformationNotSupported
    }
  }

  object ResolvesToFunction {
    def unapply(result: ScalaResolveResult): Option[PsiNamedElement] = result match {
      case ScalaResolveResult(scalaFun: ScParameterOwner, _) => Some(scalaFun)
      case ScalaResolveResult(syntheticFun: ScFun, _) => Some(syntheticFun)
      case ScalaResolveResult(javaFun: PsiMethod, _) => Some(javaFun)
      case _ => None
    }
  }
}
