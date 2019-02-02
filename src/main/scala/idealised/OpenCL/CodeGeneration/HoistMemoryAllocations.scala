package idealised.OpenCL.CodeGeneration

import idealised.DPIA.DSL._
import idealised.DPIA.ImperativePrimitives._
import idealised.DPIA.Phrases.{VisitAndRebuild, _}
import idealised.DPIA.Types._
import idealised.DPIA._
import idealised.OpenCL.ImperativePrimitives.OpenCLParFor
import idealised.OpenMP.ImperativePrimitives.ParForNat
import idealised._

object HoistMemoryAllocations {

  case class AllocationInfo(addressSpace: idealised.OpenCL.AddressSpace,
                            identifier: Identifier[VarType])

  def apply(originalPhrase: Phrase[CommandType]): (Phrase[CommandType], List[AllocationInfo]) = {
    val visitor = new VisitorScope(List[AllocationInfo]()).Visitor(List())

    val rewrittenPhrase = VisitAndRebuild(originalPhrase, visitor)

    (rewrittenPhrase, visitor.getReplacedAllocations)
    //    // Create a fresh allocation for every replaced New node using initially the
    //    // rewrittenPhrase and then the previous New node as its nested body
    //    replacedAllocations.foldLeft(rewrittenPhrase)((prev, alloc) => {
    //      val (addressSpace, identifier) = alloc
    //      New(identifier.t.t1.dataType, addressSpace, LambdaPhrase(identifier, prev))
    //    })
  }

  private class VisitorScope(var replacedAllocations: List[AllocationInfo]) {

    case class ParForInfo(parallelismLevel: idealised.OpenCL.ParallelismLevel,
                          loopIndex: Either[Identifier[ExpType],Nat],
                          length: Nat)

    case class Visitor(parForInfos: List[ParForInfo]) extends VisitAndRebuild.Visitor {

      def getReplacedAllocations: List[AllocationInfo] = replacedAllocations

      override def apply[T <: PhraseType](p: Phrase[T]): Result[Phrase[T]] = {
        p match {
          case f: For =>
            f.body match {
              case Lambda(loopIndex, _) =>
                Continue(f,
                  Visitor(ParForInfo(OpenCL.Sequential, Left(loopIndex), f.n) :: parForInfos))
              case _ => throw new Exception("This should not happen")
            }

          case f: ForNat =>
            f.body match {
              case NatDependentLambda(loopIndex, _) =>
                Continue(f,
                  Visitor(ParForInfo(OpenCL.Sequential, Right(loopIndex), f.n) :: parForInfos))
              case _ => throw new Exception("This should not happen")
            }

          case pf:ParForNat =>
            pf.body match {
              case NatDependentLambda(loopIndex, _) =>
                Continue(pf,
                  Visitor(ParForInfo(OpenCL.Global, Right(loopIndex), pf.n) :: parForInfos))
              case _ => throw new Exception("This should not happen")
            }

          // remember param and length for each `par for`
          case pf: OpenCLParFor =>
            pf.body match {
              case Lambda(loopIndex, _) =>
                Continue(pf,
                  Visitor(ParForInfo(pf.parallelismLevel, Left(loopIndex), pf.n) :: parForInfos))
              case _ => throw new Exception("This should not happen")
            }
          case New(_, addressSpace, Lambda(variable, body)) if addressSpace != OpenCL.PrivateMemory =>
            Stop(
              replaceNew(addressSpace.asInstanceOf[idealised.OpenCL.AddressSpace],
                variable, body)).asInstanceOf[Result[Phrase[T]]]
          case _ => Continue(p, this)
        }
      }

      private def replaceNew(addressSpace: idealised.OpenCL.AddressSpace,
                             variable: Identifier[VarType],
                             body: Phrase[CommandType]): Phrase[CommandType] = {
        // Replace `new` node by looking through the information from the `par for`s, ...
        val (finalVariable, finalBody) = parForInfos.foldLeft((variable, body)) {
          // ... to rewrite the new's body given the oldParam, oldBody,
          // as well as the index `i` and length `n` of a `par for` ...
          case ((oldVariable, oldBody), ParForInfo(parallelismLevel, i, n)) =>
            addressSpace match {
              case OpenCL.GlobalMemory =>
                performRewrite(oldVariable, oldBody, i, n)
              case OpenCL.LocalMemory =>
                parallelismLevel match {
                  case OpenCL.Local | OpenCL.Sequential =>
                    performRewrite(oldVariable, oldBody, i, n)
                  case OpenCL.WorkGroup => // do not perform the substitution
                    (oldVariable, oldBody)
                  case OpenCL.Global =>
                    throw new Exception("This should not happen")
                }
              case OpenCL.PrivateMemory =>
                throw new Exception("This can't happen")
            }
        }

        // ... remember `finalVariable' to regenerate the `new' at the
        // outermost scope and return the rewritten finalBody which
        // replaces the old `new` node
        replacedAllocations = AllocationInfo(addressSpace, finalVariable) :: replacedAllocations
        VisitAndRebuild(finalBody, this)
      }

      private def performRewrite(oldVariable: Identifier[VarType],
                                 oldBody: Phrase[CommandType],
                                 i: Either[Identifier[ExpType],Nat],
                                 n: Nat): (Identifier[VarType], Phrase[CommandType]) = {
        // Create `newParam' with a new type ...
        val newVariable = Identifier(oldVariable.name, VarType(dt=ArrayType(n, oldVariable.t.t1.dataType)))
        // ... and substitute all occurrences of the oldParam with
        // the newParam indexed by the `par for` index, ...
        val newBody = i match {
          case Left(identExpr) =>
            Phrase.substitute(
              substitutionMap = Map(
                π1(oldVariable) -> (π1(newVariable) `@` identExpr),
                π2(oldVariable) -> (π2(newVariable) `@` identExpr)
              ),
              in = oldBody
            )
          case Right(identNat) =>
            Phrase.substitute(
              substitutionMap = Map(
                π1(oldVariable) -> (π1(newVariable) `@` identNat),
                π2(oldVariable) -> (π2(newVariable) `@` identNat)
              ),
              in = oldBody
            )
        }
        // ... finally, return `newParam' and `newBody'.
        (newVariable, newBody)
      }
    }
  }

}
