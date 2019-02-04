package idealised.DPIA.FunctionalPrimitives

import idealised.DPIA.IntermediatePrimitives.ScanSeqI
import idealised.DPIA.Phrases._
import idealised.DPIA.Types._
import idealised.DPIA._

//noinspection TypeAnnotation
final case class ScanSeq(n: Nat,
                         dt1: DataType,
                         dt2: DataType,
                         f: Phrase[ExpType -> (ExpType -> ExpType)],
                         init:Phrase[ExpType],
                         array: Phrase[ExpType])
  extends AbstractScan(n, dt1, dt2, f, init, array) {
  override def makeScan = ScanSeq
  override def makeScanI = ScanSeqI
}