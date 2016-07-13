package OpenCL.HighLevelCombinators

import Core._
import HighLevelCombinators.AbstractMap
import MidLevelCombinators.AbstractMapI
import OpenCL.MidLevelCombinators.MapGlobalI

final case class MapGlobal(n: Nat,
                           dt1: DataType,
                           dt2: DataType,
                           f: Phrase[ExpType -> ExpType],
                           array: Phrase[ExpType])
  extends AbstractMap(n, dt1, dt2, f, array) {
  override def makeMap = MapGlobal

  override def makeMapI = MapGlobalI
}
