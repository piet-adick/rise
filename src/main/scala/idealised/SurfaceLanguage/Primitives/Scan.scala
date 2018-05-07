package idealised.SurfaceLanguage.Primitives

import idealised.DPIA
import idealised.SurfaceLanguage.DSL._
import idealised.SurfaceLanguage.Types.TypeInference.SubstitutionMap
import idealised.SurfaceLanguage.Types._
import idealised.SurfaceLanguage.VisitAndRebuild.Visitor
import idealised.SurfaceLanguage._


/**
  * Created by federico on 12/01/18.
  */
abstract class AbstractScan(f:Expr[DataType -> (DataType -> DataType)],
                            init:DataExpr,
                            array:DataExpr,
                            override val `type`:Option[DataType]) extends PrimitiveExpr{

  def makeScan:(Expr[DataType -> (DataType -> DataType)], DataExpr, DataExpr, Option[DataType]) => AbstractScan

  type DPIABinaryFunctionType = DPIA.Types.FunctionType[DPIA.Types.ExpType, DPIA.Types.FunctionType[DPIA.Types.ExpType, DPIA.Types.ExpType]]

  def makeDPIAScan: (
    DPIA.Nat,
      DPIA.Types.DataType,
      DPIA.Types.DataType,
      DPIA.Phrases.Phrase[DPIABinaryFunctionType],
      DPIA.Phrases.Phrase[DPIA.Types.ExpType],
      DPIA.Phrases.Phrase[DPIA.Types.ExpType]
    ) => DPIA.FunctionalPrimitives.AbstractScan


  override def inferType(subs: SubstitutionMap): DataExpr = {
    import TypeInference._
    val array_ = TypeInference(array, subs)
    val init_ = TypeInference(init, subs)
    (init_.`type`, array_.`type`) match {
      case (Some(dt2), Some(ArrayType(n, dt1))) =>
        val f_ = setParamsAndInferTypes(f, dt1, dt2, subs)
        f_.`type` match {
          case Some(FunctionType(t1, FunctionType(t2, t3))) =>
            if (dt1 == t1 && dt2 == t2 && dt2 == t3) {
              makeScan(f_, init_, array_, Some(dt2))
            } else {
              error(this.toString,
                dt1.toString + ", " + t1.toString + " as well as " +
                  dt2.toString + ", " + t2.toString + " and " + t3.toString,
                expected = "them to match")
            }
          case x => error(expr = s"${this.getClass.getSimpleName}($f_, $init_, $array_)",
            found = s"`${x.toString}'", expected = "dt1 -> (dt2 -> dt3)")
        }
      case x => error(expr = s"${this.getClass.getSimpleName}($f, $init_, $array_)",
        found = s"`${x.toString}'", expected = "(dt1, n.dt2)")
    }
  }

  override def visitAndRebuild(fun: Visitor): DataExpr =
    makeScan(VisitAndRebuild(this.f, fun), VisitAndRebuild(this.init, fun), VisitAndRebuild(this.array, fun), this.`type`.map(fun(_)))

  override def convertToPhrase:DPIA.FunctionalPrimitives.AbstractScan = {
    (f.`type`, array.`type`) match {
      case (Some(FunctionType(dt1, FunctionType(dt2, _))), Some(ArrayType(n, dt1_))) if dt1 == dt1_ =>
        makeDPIAScan(n, dt1, dt2,
          f.toPhrase[DPIABinaryFunctionType],
          init.toPhrase[DPIA.Types.ExpType],
          array.toPhrase[DPIA.Types.ExpType]
        )
      case _ => throw new Exception("")
    }
  }
}

final case class Scan(f: Expr[DataType -> (DataType -> DataType)], init:DataExpr, array: DataExpr,
                     override val `type`: Option[DataType] = None)
  extends AbstractScan(f, init, array, `type`) {

  override def makeDPIAScan = DPIA.FunctionalPrimitives.Scan

  override def makeScan = Scan
}