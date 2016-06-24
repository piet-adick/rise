package CommandPatterns

import Core._
import Core.OperationalSemantics._
import opencl.generator.OpenCLAST.Block
import Compiling.SubstituteImplementations

case class Skip() extends CommandPattern {

  override def typeCheck(): CommandType = CommandType()

  override def eval(s: Store): Store = s

  override def visitAndRebuild(f: VisitAndRebuild.fun): Phrase[CommandType] = this

  override def substituteImpl(env: SubstituteImplementations.Environment): Phrase[CommandType] = this

  override def toOpenCL(b: Block, ocl: ToOpenCL): Block = b

  override def prettyPrint: String = ""

}
