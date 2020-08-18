package rise.cuda

import rise.cuda.primitives._
import rise.core.DSL._
import rise.core._

object DSL {
  object mapBlock {
    def apply(): MapBlock = MapBlock('x')()
    def apply(e: Expr): Expr = MapBlock('x')()(e)
    def apply(dim: Char): Expr = MapBlock(dim)()
  }

  object mapGlobal {
    def apply(): MapGlobal = MapGlobal('x')()
    def apply(e: Expr): Expr = MapGlobal('x')()(e)
    def apply(dim: Char): Expr = MapGlobal(dim)()
  }

  object mapThreads {
    def apply(): MapThreads = MapThreads('x')()
    def apply(e: Expr): Expr = MapThreads('x')()(e)
    def apply(dim: Char): Expr = MapThreads(dim)()
  }

  object mapWarp {
    def apply(): MapWarp = MapWarp('x')()
    def apply(e: Expr): Expr = MapWarp('x')()(e)
    def apply(dim: Char): Expr = MapWarp(dim)()
  }

  object mapLane {
    def apply(): MapLane = MapLane('x')()
    def apply(e: Expr): Expr = MapLane('x')()(e)
    def apply(dim: Char): Expr = MapLane(dim)()
  }

}