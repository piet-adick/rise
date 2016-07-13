package OpenCL.MidLevelCombinators

import Compiling.SubstituteImplementations
import Core._
import DSL.typed._
import MidLevelCombinators.AbstractMapI
import SubstituteImplementations._
import OpenCL.LowLevelCombinators.ParForGlobal

final case class MapGlobalI(n: Nat,
                            dt1: DataType,
                            dt2: DataType,
                            out: Phrase[AccType],
                            f: Phrase[AccType -> (ExpType -> CommandType)],
                            in: Phrase[ExpType])
  extends AbstractMapI(n, dt1, dt2, out, f, in) {

  override def makeMapI = MapGlobalI

  override def substituteImpl(env: Environment): Phrase[CommandType] = {
    ParForGlobal(n, dt2, out, λ(exp"[$int]")(i => λ(acc"[$dt2]")(o =>
      SubstituteImplementations(f(o)(in `@` i), env)
    )))
  }

}
