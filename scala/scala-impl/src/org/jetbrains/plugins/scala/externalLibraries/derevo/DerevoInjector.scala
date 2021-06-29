package org.jetbrains.plugins.scala.externalLibraries.derevo

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScTypeAliasDefinition, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScTemplateDefinition, ScTypeDefinition, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScParameterizedType, ScCompoundType}
import org.jetbrains.plugins.scala.project.ProjectContext

import scala.annotation.tailrec

class DerevoInjector extends SyntheticMembersInjector {
  trait TypeConstructor {
    def apply(tpe: String): String
    def qualifiedName: String
  }

  case class DeriveAnnotParams(
    keepRefinements: Boolean,
    tFrom: TypeConstructor,
    tTo: TypeConstructor
  )

  def extractAnnotParams(derivationObject: ScObject): Option[DeriveAnnotParams] = {
    val supertypes = derivationObject.extendsBlock.superTypes
    val keepRefinements = supertypes.exists {
      case d: ScDesignatorType =>
        d.extractDesignated(true).exists { case t: ScTrait => t.qualifiedName == "derevo.KeepRefinements"; case _ => false }
      case _ =>
        false
    }

    @tailrec def typeargToTc(typearg: ScType): Option[TypeConstructor] = typearg match {
      case d: ScDesignatorType => d.extractDesignated(false).flatMap {
        case t: ScTemplateDefinition => Some(new TypeConstructor {
          override def apply(tpe: String): String = s"_root_.${t.qualifiedName}[$tpe]"

          override def qualifiedName: String = "_root_." + t.qualifiedName
        })
        case _ => None
      }

      case p: ScProjectionType => p.projected match {
        case d: ScDesignatorType => p.element -> d.extractDesignated(true) match {
          case (t: ScTrait, Some(_: ScObject)) => typeargToTc(ScDesignatorType(t))
          case _ => None
        }

        case c: ScCompoundType => c.typesMap.get(p.element.getName) match {
          case Some(t) if t.typeParams.size == 1 => t.typeAlias match {
            case d: ScTypeAliasDefinition => d.aliasedType match {
              case Right(p: ScParameterizedType) => p.extractDesignated(true) match {
                case Some(designated: ScTypeDefinition) =>
                  val tc = new TypeConstructor {
                    override def apply(tpe: String): String = {
                      val fixedTpeArgs = p.typeArguments.map {
                        case _: TypeParameterType => tpe
                        case t => t.canonicalText
                      }

                      s"_root_.${designated.qualifiedName}[${fixedTpeArgs.mkString(", ")}]"
                    }

                    override def qualifiedName: String = "_root_." + designated.qualifiedName
                  }

                  Some(tc)
                case _ => None
              }
              case _ => None
            }
            case _ => None
          }
          case _ => None
        }
        case _ => None
      }
      case _ => None
    }

    supertypes
      .collect { case s: ScParameterizedType => s }
      .map(s => s.extractDesignated(true) -> s.typeArguments)
      .collectFirst {
        case (Some(t: ScTrait), Seq(typearg)) if t.qualifiedName == "derevo.Derivation" =>
          typeargToTc(typearg).map(x => DeriveAnnotParams(keepRefinements, x, x))
        case (Some(t: ScTrait), Seq(from, to, _)) if t.qualifiedName == "derevo.SpecificDerivation" =>
          typeargToTc(from).zip(typeargToTc(to)).map { case (from, to) => DeriveAnnotParams(keepRefinements, from, to) }
      }
      .flatten
  }

  def extractTypeclassTpeFromExpr(expr: ScExpression, methodCallResultTpe: Option[ScType]): Option[(TypeConstructor, TypeConstructor)] = {
    def tcForParameterizedType(a: ScParameterizedType, to: TypeConstructor): TypeConstructor = new TypeConstructor {
      override def apply(tpe: String): String = s"${a.designator.canonicalText}[$tpe, ${a.typeArguments.drop(1).map(_.canonicalText).mkString(", ")}]"
      override def qualifiedName: String = to.qualifiedName
    }

    def tcForCompoundType(c: ScCompoundType, firstComponent: ScParameterizedType, to: TypeConstructor): TypeConstructor = new TypeConstructor {
      override def apply(tpe: String): String = {
        implicit val ctx: ProjectContext = c.projectContext
        val fc = s"${firstComponent.designator.canonicalText}[$tpe, ${firstComponent.typeArguments.drop(1).map(_.canonicalText).mkString(", ")}]"


        c.components.size match {
          case 0 | 1 => fc + " " + c.copy(components = Nil).canonicalText
          case _ => fc + " with " + c.copy(components = c.components.drop(1)).canonicalText
        }
      }

      override def qualifiedName: String = to.qualifiedName
    }

    def extractTcsFromDefDef(f: ScFunction): Option[(TypeConstructor, TypeConstructor)] = f.containingClass match {
      case o: ScObject => extractAnnotParams(o).flatMap {
        case DeriveAnnotParams(true, _, to) => methodCallResultTpe.orElse(f.returnType.toOption) match {
          case Some(a: ScParameterizedType) =>
            val ktc = tcForParameterizedType(a, to)

            Some(ktc -> ktc)
          case Some(a: ScCompoundType) =>
            a.components.headOption match {
              case Some(p: ScParameterizedType) =>
                val ktc = tcForCompoundType(a, p, to)

                Some(ktc -> ktc)
              case _ => None
            }
          case _ => None
        }
        case DeriveAnnotParams(_, from, to) => Some(from -> to)
      }
      case _ => None
    }

    def checkParamClauses(f: ScFunction) =
      f.paramClauses.clauses.forall(c => c.isImplicit || c.parameters.isEmpty)

    expr match {
      case r: ScReferenceExpression =>
        r.shapeResolve.flatMap { shape =>
          shape.element match {
            case o: ScObject =>
              def fn(name: String) = o.members.collectFirst {
                case f: ScFunction if f.name == name && checkParamClauses(f) => f
              }

              fn("instance").orElse(fn("apply")).flatMap(extractTcsFromDefDef)
            case f: ScFunctionDefinition =>
              extractTcsFromDefDef(f)

            case _ => None
          }
        }.collectFirst { case v => v }
      case _ => None
    }
  }

  def tryGenerateImplicitForReferenceExpr(ix: Int, obj: ScObject, expr: ScExpression, methodCallResultType: Option[ScType]): Option[String] =
    extractTypeclassTpeFromExpr(expr, methodCallResultType)
      .map { case (tpeFrom, tpeTo) =>
        val tparams = obj.fakeCompanionClassOrCompanionClass.asInstanceOf[ScTypeDefinition].typeParameters

        if (tparams.nonEmpty) {
          val tparamStr = tparams.map(_.name).mkString(", ")

          val implicits = tparams.zipWithIndex.flatMap { case (tp, ix) =>
            tp.findAnnotationNoAliases("derevo.phantom") match {
              case _: ScAnnotation => None
              case _ => Some(s"ev$ix: ${tpeFrom.apply(tp.name)}")
            }
          }

          val implicitStr = if (implicits.isEmpty) "" else s"(implicit ${implicits.mkString(", ")})"

          s"implicit def idea$$injected_$ix[$tparamStr]$implicitStr: ${tpeTo(s"${obj.name}[$tparamStr]")} = ???"
        } else {
          s"implicit val idea$$injected_$ix: ${tpeTo(obj.name)} = ???"
        }
      }

  def deriveAnnotArgs(source: PsiClass): Seq[ScExpression] = source match {
    case tpe: ScTypeDefinition =>
      tpe.findAnnotationNoAliases("derevo.derive") match {
        case annot: ScAnnotation => annot.annotationExpr.getAnnotationParameters
        case _ => Nil
      }
    case _ => Nil
  }

  override def needsCompanionObject(source: ScTypeDefinition): Boolean = deriveAnnotArgs(source).nonEmpty

  override def injectMembers(source: ScTypeDefinition): Seq[String] = source match {
    case o: ScObject =>
      deriveAnnotArgs(o.fakeCompanionClassOrCompanionClass).zipWithIndex.flatMap {
        case (r: ScReferenceExpression, ix) =>
          tryGenerateImplicitForReferenceExpr(ix, o, r, None)
        case (m: ScMethodCall, ix) =>
          m.getChildren.headOption.collect { case s: ScReferenceExpression => s }.flatMap(tryGenerateImplicitForReferenceExpr(ix, o, _, m.`type`().toOption))
        case _ => None
      }
    case _ => Nil
  }
}
