package CommandPatterns

import Core.OperationalSemantics.newName
import Core.PhraseType._
import Core._
import apart.arithmetic.{NamedVar, RangeAdd}
import opencl.generator.OpenCLAST._
import opencl.generator.{get_global_id, get_global_size}


case class ParForGlobal(override val n: Phrase[ExpType],
                        override val out: Phrase[AccType],
                        override val body: Phrase[ExpType -> (AccType -> CommandType)])
  extends AbstractParFor(n, out, body) {

  override val makeParFor = ParForGlobal

  override val name: NamedVar =
    NamedVar(newName())

  override lazy val init: Declaration =
    VarDecl(name.name, opencl.ir.Int,
      init = ArithExpression(get_global_id(0, RangeAdd(0, ocl.globalSize, 1))),
      addressSpace = opencl.ir.PrivateMemory)

  override lazy val cond: ExpressionStatement =
    CondExpression(VarRef(name.name),
      ToOpenCL.exp(n, ocl),
      CondExpression.Operator.<)

  override lazy val increment: Expression =
    AssignmentExpression(ArithExpression(name),
      ArithExpression(name + get_global_size(0, RangeAdd(ocl.globalSize, ocl.globalSize + 1, 1))))

}
