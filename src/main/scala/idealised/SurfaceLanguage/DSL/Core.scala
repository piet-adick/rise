package idealised.SurfaceLanguage.DSL

import idealised.DPIA.LetNatIdentifier
import idealised.SurfaceLanguage.Types._
import idealised.SurfaceLanguage._

object identifier {
  def apply(name: String): IdentifierExpr = IdentifierExpr(name, None)

  def apply(name: String, dt: DataType): IdentifierExpr = IdentifierExpr(name, Some(dt))
}

object fun {
  def apply[T <: Type](f: IdentifierExpr => Expr): Expr = {
    val param = identifier(newName())
    LambdaExpr(param, f(param))
  }

  def apply[T <: Type](f: (IdentifierExpr, IdentifierExpr) => Expr): Expr = {
    val p1 = identifier(newName())
    val p2 = identifier(newName())
    LambdaExpr(p1, LambdaExpr(p2, f(p1, p2)))
  }

  def apply[T <: Type](dt: DataType)
                      (f: IdentifierExpr => Expr): Expr = {
    val param = identifier(newName(), dt)
    LambdaExpr(param, f(param))
  }

}

object nFun {
  def apply[T <: Type](f: (NatIdentifier, NatIdentifier, NatIdentifier, NatIdentifier, NatIdentifier) => Expr): Expr = {
    val p1 = NatIdentifier(newName())
    val p2 = NatIdentifier(newName())
    val p3 = NatIdentifier(newName())
    val p4 = NatIdentifier(newName())
    val p5 = NatIdentifier(newName())
    NatDependentLambdaExpr(p1, NatDependentLambdaExpr(p2,
      NatDependentLambdaExpr(p3, NatDependentLambdaExpr(p4, NatDependentLambdaExpr(p5, f(p1, p2, p3, p4, p5))))))
  }

  def apply[T <: Type](f: (NatIdentifier, NatIdentifier, NatIdentifier, NatIdentifier) => Expr): Expr = {
    val p1 = NatIdentifier(newName())
    val p2 = NatIdentifier(newName())
    val p3 = NatIdentifier(newName())
    val p4 = NatIdentifier(newName())
    NatDependentLambdaExpr(p1, NatDependentLambdaExpr(p2,
      NatDependentLambdaExpr(p3, NatDependentLambdaExpr(p4, f(p1, p2, p3, p4)))))
  }

  def apply[T <: Type](f: (NatIdentifier, NatIdentifier, NatIdentifier) => Expr): Expr = {
    val p1 = NatIdentifier(newName())
    val p2 = NatIdentifier(newName())
    val p3 = NatIdentifier(newName())
    NatDependentLambdaExpr(p1, NatDependentLambdaExpr(p2, NatDependentLambdaExpr(p3, f(p1, p2, p3))))
  }

  def apply[T <: Type](f: (NatIdentifier, NatIdentifier) => Expr): Expr = {
    val p1 = NatIdentifier(newName())
    val p2 = NatIdentifier(newName())
    NatDependentLambdaExpr(p1, NatDependentLambdaExpr(p2, f(p1, p2)))
  }

  def apply[T <: Type](f: NatIdentifier => Expr): NatDependentLambdaExpr = {
    val x = NatIdentifier(newName())
    NatDependentLambdaExpr(x, f(x))
  }
}

object tFun {
  def apply[T <: Type](f: DataTypeIdentifier => Expr): TypeDependentLambdaExpr = {
    val x = DataTypeIdentifier(newName())
    TypeDependentLambdaExpr(x, f(x))
  }

}

object letNat {
  def apply(defn:Expr, makeBody:LetNatIdentifier => Expr):LetNat = {
    val identifier = LetNatIdentifier()
    LetNat(identifier, defn, makeBody(identifier))
  }
}
