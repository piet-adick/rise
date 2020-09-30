package rise.cuda

import primitiveMacro.Primitive.primitive
import rise.core.TypeLevelDSL._
import rise.core.types._

object primitives {
  sealed trait Primitive extends rise.core.Primitive

  protected def mapTypeScheme: Type =
    implN(n =>
      implDT(s =>
        implDT(t => (s ->: t) ->: ArrayType(n, s) ->: ArrayType(n, t))
      )
    )

  @primitive case class MapBlock(dim: Char)(override val t: Type = TypePlaceholder)
    extends Primitive {
    override def typeScheme: Type = mapTypeScheme
  }

  @primitive case class MapGlobal(dim: Char)(override val t: Type = TypePlaceholder)
    extends Primitive {
    override def typeScheme: Type = mapTypeScheme
  }

  @primitive case class MapThreads(dim: Char)(override val t: Type = TypePlaceholder)
    extends Primitive {
    override def typeScheme: Type = mapTypeScheme
  }

  @primitive case class MapWarp(dim: Char)(override val t: Type = TypePlaceholder)
    extends Primitive {
    override def typeScheme: Type = mapTypeScheme
  }

  @primitive case class MapLane(dim: Char)(override val t: Type = TypePlaceholder)
    extends Primitive {
    override def typeScheme: Type = mapTypeScheme
  }

  @primitive case class ShflWarp()(override val t: Type = TypePlaceholder)
    extends Primitive {
    private val warpSize = 32

    override def typeScheme: Type =
      implST(dt =>
        ArrayType(warpSize, IndexType(warpSize)) ->:
        ArrayType(warpSize, dt) ->:
        ArrayType(warpSize, dt)
      )
  }

  @primitive case class ShflDownWarp()(override val t: Type = TypePlaceholder)
    extends Primitive {
    private val warpSize = 32

    override def typeScheme: Type =
      implST(dt =>
        nFunT(n =>
          ArrayType(warpSize, dt) ->:
            ArrayType(warpSize, dt)
        )
      )
  }

  @primitive case class ShflUpWarp()(override val t: Type = TypePlaceholder)
    extends Primitive {
    private val warpSize = 32

    override def typeScheme: Type =
      implST(dt =>
        nFunT(n =>
          ArrayType(warpSize, dt) ->:
            ArrayType(warpSize, dt)
        )
      )
  }

  @primitive case class ShflXorWarp()(override val t: Type = TypePlaceholder)
    extends Primitive {
    private val warpSize = 32

    override def typeScheme: Type =
      implST(dt =>
        nFunT(n =>
          ArrayType(warpSize, dt) ->:
            ArrayType(warpSize, dt)
        )
      )
  }

}