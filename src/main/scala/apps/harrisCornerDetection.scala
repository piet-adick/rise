package apps

import lift.core._
import lift.core.DSL._
import lift.core.types._
import lift.core.primitives.{typeHole, _}
import lift.OpenCL.primitives._
import lift.core.HighLevelConstructs._
import util.gen

import scala.reflect.ClassTag

object harrisCornerDetection {
  private val C2D = separableConvolution2D
  private val id = C2D.id
  private val mulT = C2D.mulT
  private val sq = fun(x => x * x)
  private def zip2D(a: Expr, b: Expr) = zipND(2)(a)(b)

  // 5 for two scalar stencils of 3
  private val hRange = lift.arithmetic.RangeAdd(5, lift.arithmetic.PosInf, 1)
  // 12 for one vector stencil of 3 (includes 2 scalar stencils of 3)
  private val wRange = lift.arithmetic.RangeAdd(12, lift.arithmetic.PosInf, 4)
  private val sobelX = nFun(hRange, h => nFun(wRange, w => fun(
    (h`.`w`.`float) ->: (h`.`w`.`float)
  )(a =>
    C2D.regRotPar(C2D.sobelXWeightsV)(C2D.sobelXWeightsH)(a)
  )))
  private val sobelY = nFun(hRange, h => nFun(wRange, w => fun(
    (h`.`w`.`float) ->: (h`.`w`.`float)
  )(a =>
    C2D.regRotPar(C2D.sobelYWeightsV)(C2D.sobelYWeightsH)(a)
  )))
  private val mul = nFun(hRange, h => nFun(wRange, w => fun(
    (h`.`w`.`float) ->: (h`.`w`.`float) ->: (h`.`w`.`float)
  )((a, b) =>
    zip(a, b) |> mapGlobal(fun(ab =>
      zip(asVectorAligned(4)(ab._1))(asVectorAligned(4)(ab._2)) |>
      mapSeq(mulT) >>
      asScalar
    ))
  )))
  private val gaussian = nFun(hRange, h => nFun(wRange, w => fun(
    (h`.`w`.`float) ->: (h`.`w`.`float)
  )(a =>
    C2D.regRotPar(C2D.binomialWeightsV)(C2D.binomialWeightsH)(a)
  )))
  private val coarsityVector = fun(sxx => fun(sxy => fun(syy => fun(kappa => {
    val det = sxx * syy - sxy * sxy
    val trace = sxx + syy
    det - vectorFromScalar(kappa) * trace * trace
  }))))
  private val coarsity = nFun(hRange, h => nFun(wRange, w => fun(
    (h`.`w`.`float) ->: (h`.`w`.`float) ->: (h`.`w`.`float) ->: float ->: (h`.`w`.`float)
  )((sxx, sxy, syy, kappa) =>
    zip(sxx, zip(sxy, syy)) |> mapGlobal(fun(s =>
      zip(asVectorAligned(4)(s._1))(zip(asVectorAligned(4)(s._2._1))(asVectorAligned(4)(s._2._2))) |>
      mapSeq(fun(s => {
        val sxx = fst(s)
        val sxy = fst(snd(s))
        val syy = snd(snd(s))
        coarsityVector(sxx)(sxy)(syy)(kappa)
      })) >>
      asScalar
    ))
  )))

  private val shuffle =
    asScalar >> drop(3) >> take(6) >> slide(4)(1) >> join >> asVector(4)
  private val sobelXYMuls = nFun(hRange, h => nFun(wRange, w => fun(
    (h`.`w`.`float) ->: (3`.`h`.`w`.`float) // (h`.`w`.`float) x ((h`.`w`.`float) x (h`.`w`.`float)))
  )(input => {
    input |>
    map(fun(w`.`float)(x =>
      x |> asVectorAligned(4)
        |> padCst(1)(0)(vectorFromScalar(x `@` lidx(0, w)))
        |> padCst(0)(1)(vectorFromScalar(x `@` lidx(w - 1, w)))
    )) |> padClamp(1)(1) |> slide(3)(1) |> mapGlobal(transpose >>
      map(fun(vNbh =>
        array(2)(C2D.sobelXWeightsV)(C2D.sobelYWeightsV) |>
        map(fun(vWs => C2D.weightsSeqVecUnroll(vWs)(vNbh)))
      )) >>
      oclSlideSeq(slideSeq.Values)(AddressSpace.Private)(3)(1)(mapSeqUnroll(id))(
        transpose >> map(shuffle) >>
          zip(array(2)(C2D.sobelXWeightsH)(C2D.sobelYWeightsH)) >>
          // TODO: this triggers an extra copy
          toPrivateFun(mapSeqUnroll(fun(hWsNbh =>
            C2D.weightsSeqVecUnroll(hWsNbh._1)(hWsNbh._2)
          ))) >>
          let(fun(ixiy => {
            val ix = ixiy `@` lidx(0, 2)
            val iy = ixiy `@` lidx(1, 2)
            array(3)(sq(ix))(ix * iy)(sq(iy)) |> mapSeqUnroll(id)
          }))
      ) >> transpose >> map(asScalar)
    ) >> transpose
/* TODO? output tuples
            pair(sq(ix), pair(ix * iy, sq(iy)))
          }))
      ) >>
      unzip >> mapSnd(unzip) >>
      mapFst(asScalar) >> mapSnd(mapFst(asScalar) >> mapSnd(asScalar))
    ) >>
    unzip >> mapSnd(unzip) >>
*/
  })))
  private val gaussianCoarsity = nFun(hRange, h => nFun(wRange, w => fun(
    (3`.`h`.`w`.`float) ->: float ->: (h`.`w`.`float)
  )((input, kappa) => {
    input |>
    map(map(fun(w`.`float)(x =>
      x |> asVectorAligned(4)
        |> padCst(1)(0)(vectorFromScalar(x `@` lidx(0, w)))
        |> padCst(0)(1)(vectorFromScalar(x `@` lidx(w - 1, w)))
    ))) |>
    map(padClamp(1)(1)) |>
    transpose |> map(transpose) |>
    slide(3)(1) |> mapGlobal(
      transpose >> map(transpose) >>
      map(map(C2D.weightsSeqVecUnroll(C2D.binomialWeightsV))) >>
      oclSlideSeq(slideSeq.Values)(AddressSpace.Private)(3)(1)(mapSeqUnroll(id))(
        transpose >> map(shuffle) >>
        toPrivateFun(mapSeqUnroll(C2D.weightsSeqVecUnroll(C2D.binomialWeightsH))) >>
        let(fun(s => {
          val sxx = s `@` lidx(0, 3)
          val sxy = s `@` lidx(1, 3)
          val syy = s `@` lidx(2, 3)
          coarsityVector(sxx)(sxy)(syy)(kappa)
        }))
      ) >> asScalar
    )
  })))

  private val sobelXY = nFun(hRange, h => nFun(wRange, w => fun(
    (h`.`w`.`float) ->: (2`.`h`.`w`.`float)
  )(input =>
    input |>
      map(fun(w`.`float)(x =>
        x |> asVectorAligned(4)
          |> padCst(1)(0)(vectorFromScalar(x `@` lidx(0, w)))
          |> padCst(0)(1)(vectorFromScalar(x `@` lidx(w - 1, w)))
      )) |> padClamp(1)(1) |> slide(3)(1) |> mapGlobal(transpose >>
      map(fun(vNbh =>
        array(2)(C2D.sobelXWeightsV)(C2D.sobelYWeightsV) |>
          map(fun(vWs => C2D.weightsSeqVecUnroll(vWs)(vNbh)))
      )) >>
      oclSlideSeq(slideSeq.Values)(AddressSpace.Private)(3)(1)(mapSeqUnroll(id))(
        transpose >> map(shuffle) >>
          zip(array(2)(C2D.sobelXWeightsH)(C2D.sobelYWeightsH)) >>
          mapSeqUnroll(fun(hWsNbh =>
            C2D.weightsSeqVecUnroll(hWsNbh._1)(hWsNbh._2)
          ))
      ) >> transpose >> map(asScalar)
    ) >> transpose
  )))
  private val mulGaussianCoarsity = nFun(hRange, h => nFun(wRange, w => fun(
    (2`.`h`.`w`.`float) ->: float ->: (h`.`w`.`float)
  )((input, kappa) =>
    input |>
    map(map(fun(w`.`float)(x =>
      x |> asVectorAligned(4)
        |> padCst(1)(0)(vectorFromScalar(x `@` lidx(0, w)))
        |> padCst(0)(1)(vectorFromScalar(x `@` lidx(w - 1, w)))
    ))) |>
    map(padClamp(1)(1)) |>
    transpose |> map(transpose) |> // H.W.2.vf
    map(map(fun(ixiy => {
      val ix = ixiy `@` lidx(0, 2)
      val iy = ixiy `@` lidx(1, 2)
      array(3)(sq(ix))(ix * iy)(sq(iy))
    }))) |>
    slide(3)(1) |> mapGlobal(
      transpose >> map(transpose) >>
      map(map(C2D.weightsSeqVecUnroll(C2D.binomialWeightsV))) >>
      oclSlideSeq(slideSeq.Values)(AddressSpace.Private)(3)(1)(mapSeqUnroll(id))(
        transpose >> map(shuffle) >>
        toPrivateFun(mapSeqUnroll(C2D.weightsSeqVecUnroll(C2D.binomialWeightsH))) >>
        let(fun(s => {
          val sxx = s `@` lidx(0, 3)
          val sxy = s `@` lidx(1, 3)
          val syy = s `@` lidx(2, 3)
          coarsityVector(sxx)(sxy)(syy)(kappa)
        }))
      ) >> asScalar
    )
  )))

  private val sobelXYMulGaussianCoarsity = nFun(hRange, h => nFun(wRange, w => fun(
    (h`.`w`.`float) ->: float ->: (h`.`w`.`float)
  )((input, kappa) =>
    input // TODO
  )))

  /* TODO?
  val threshold = coarsity :>> mapSeq(mapSeq(fun(c =>
    if (c <= threshold) { 0.0f } else { c }
  )))

  val edge = slide3x3(coarsity) :>> mapSeq(mapSeq(fun(nbh =>
    forall i != center, nbh[center] > nbh[i] ?
  )))
  */

  import idealised.OpenCL._
  import util.{Time, TimeSpan}

  // n x m . t => n.m.t
  def as2D[A: ClassTag, B](m: Int): ((Array[A], B)) => (Array[Array[A]], B) = {
    case (a, t) => (a.sliding(m, m).toArray, t)
  }
  // n x m x o . t => n.m.o.t
  def as3D[A: ClassTag, B](m: Int, o: Int): ((Array[A], B)) => (Array[Array[Array[A]]], B) = x => {
    val as2Do = as2D[Array[A], B](m)
    val as2Di = as2D[A, B](o)
    as2Do(as2Di(x))
  }

  case class NoPipe(sobelX: KernelNoSizes,
                    sobelY: KernelNoSizes,
                    mul: KernelNoSizes,
                    gaussian: KernelNoSizes,
                    coarsity: KernelNoSizes) {
    def run(input: Array[Array[Float]],
            kappa: Float): (Array[Float], Seq[(String, TimeSpan[Time.ms])]) = {
      val H = input.length
      val W = input(0).length
      def as2DW[B] = as2D[Float, B](W)

      val localSize = LocalSize(1)
      val globalSize = GlobalSize(H)

      val fSx = sobelX.as[ScalaFunction `(`
        Int `,` Int `,` Array[Array[Float]]
      `)=>` Array[Float]]
      val (ix, ixt) = as2DW(fSx(localSize, globalSize)(H `,` W `,` input))

      val fSy = sobelY.as[ScalaFunction `(`
        Int `,` Int `,` Array[Array[Float]]
        `)=>` Array[Float]]
      val (iy, iyt) = as2DW(fSy(localSize, globalSize)(H `,` W `,` input))

      val fMul = mul.as[ScalaFunction `(`
        Int `,` Int `,` Array[Array[Float]] `,` Array[Array[Float]]
        `)=>` Array[Float]]
      val (ixx, ixxt) = as2DW(fMul(localSize, globalSize)(H `,` W `,` ix `,` ix))
      val (ixy, ixyt) = as2DW(fMul(localSize, globalSize)(H `,` W `,` ix `,` iy))
      val (iyy, iyyt) = as2DW(fMul(localSize, globalSize)(H `,` W `,` iy `,` iy))

      val fG = gaussian.as[ScalaFunction `(`
        Int `,` Int `,` Array[Array[Float]]
        `)=>` Array[Float]]
      val (sxx, sxxt) = as2DW(fG(localSize, globalSize)(H `,` W `,` ixx))
      val (sxy, sxyt) = as2DW(fG(localSize, globalSize)(H `,` W `,` ixy))
      val (syy, syyt) = as2DW(fG(localSize, globalSize)(H `,` W `,` iyy))

      val fC = coarsity.as[ScalaFunction `(`
        Int `,` Int `,` Array[Array[Float]] `,` Array[Array[Float]] `,` Array[Array[Float]] `,` Float
        `)=>` Array[Float]]
      val (k, kt) = fC(localSize, globalSize)(H `,` W `,` sxx `,` sxy `,` syy `,` kappa)

      (k, Seq(
        "Ix" -> ixt,
        "Iy" -> iyt,
        "Ixx" -> ixxt,
        "Ixy" -> ixyt,
        "Iyy" -> iyyt,
        "Sxx" -> sxxt,
        "Sxy" -> sxyt,
        "Syy" -> syyt,
        "K" -> kt
      ))
    }
  }

  object NoPipe {
    def create: NoPipe = NoPipe(
      gen.OpenCLKernel(sobelX),
      gen.OpenCLKernel(sobelY),
      gen.OpenCLKernel(mul),
      gen.OpenCLKernel(gaussian),
      gen.OpenCLKernel(coarsity)
    )
  }

  case class HalfPipe2(sobelXYMuls: KernelNoSizes,
                       gaussianCoarsity: KernelNoSizes) {
    def run(input: Array[Array[Float]],
            kappa: Float): (Array[Float], Seq[(String, TimeSpan[Time.ms])]) = {
      val H = input.length
      val W = input(0).length

      val localSize = LocalSize(1)
      val globalSize = GlobalSize(H)

      val fSxyM = sobelXYMuls.as[ScalaFunction `(`
        Int `,` Int `,` Array[Array[Float]]
        `)=>` Array[Float]]
      def asIs[B] = as3D[Float, B](H, W)
      val (is, ist) = asIs(fSxyM(localSize, globalSize)(H `,` W `,` input))

      val fGC = gaussianCoarsity.as[ScalaFunction `(`
        Int `,` Int `,` Array[Array[Array[Float]]] `,` Float
      `)=>` Array[Float]]
      val (k, kt) = fGC(localSize, globalSize)(H `,` W `,` is `,` kappa)

      (k, Seq(
        "Ixx, Ixy, Iyy" -> ist,
        "K" -> kt
      ))
    }
  }

  object HalfPipe2 {
    def create: HalfPipe2 =
      HalfPipe2(
        gen.OpenCLKernel(sobelXYMuls),
        gen.OpenCLKernel(gaussianCoarsity)
      )
  }

  case class HalfPipe1(sobelXY: KernelNoSizes,
                       mulGaussianCoarsity: KernelNoSizes) {
    def run(input: Array[Array[Float]],
            kappa: Float): (Array[Float], Seq[(String, TimeSpan[Time.ms])]) = {
      val H = input.length
      val W = input(0).length

      val localSize = LocalSize(1)
      val globalSize = GlobalSize(H)

      val fSxy = sobelXY.as[ScalaFunction `(`
        Int `,` Int `,` Array[Array[Float]]
        `)=>` Array[Float]]
      def asIs[B] = as3D[Float, B](H, W)
      val (is, ist) = asIs(fSxy(localSize, globalSize)(H `,` W `,` input))

      val fMGC = mulGaussianCoarsity.as[ScalaFunction `(`
        Int `,` Int `,` Array[Array[Array[Float]]] `,` Float
        `)=>` Array[Float]]
      val (k, kt) = fMGC(localSize, globalSize)(H `,` W `,` is `,` kappa)

      (k, Seq(
        "Ix, Iy" -> ist,
        "K" -> kt
      ))
    }
  }

  object HalfPipe1 {
    def create: HalfPipe1 =
      HalfPipe1(
        gen.OpenCLKernel(sobelXY),
        gen.OpenCLKernel(mulGaussianCoarsity)
      )
  }

  case class FullPipe(sobelXYMulGaussianCoarsity: KernelNoSizes) {
    def run(input: Array[Array[Float]],
            kappa: Float): (Array[Float], Seq[(String, TimeSpan[Time.ms])]) = {
      val H = input.length
      val W = input(0).length

      val localSize = LocalSize(1)
      val globalSize = GlobalSize(H)

      val f = sobelXYMulGaussianCoarsity.as[ScalaFunction `(`
        Int `,` Int `,` Array[Array[Float]] `,` Float
        `)=>` Array[Float]]
      val (k, kt) = f(localSize, globalSize)(H `,` W `,` input `,` kappa)

      (k, Seq("K" -> kt))
    }
  }

  object FullPipe {
    def create: FullPipe = FullPipe(gen.OpenCLKernel(sobelXYMulGaussianCoarsity))
  }

  private def zip2D[A, B](as: Array[Array[A]], bs: Array[Array[B]]): Array[Array[(A, B)]] =
    as.zip(bs).map { case (a, b) => a.zip(b) }
  private def computePointwiseGold[A: ClassTag, B: ClassTag](inputs: Array[Array[A]], f: A => B): Array[Array[B]] =
    inputs.map(r => r.map(f))

  def computeGold(h: Int, w: Int,
                  input: Array[Array[Float]],
                  kappa: Float): Array[Array[Float]] = {
    val ix = C2D.computeGold(h, w, input, C2D.sobelXWeights2d)
    val iy = C2D.computeGold(h, w, input, C2D.sobelYWeights2d)
    val ixx = computePointwiseGold(ix, { x: Float => x * x })
    val ixy = computePointwiseGold[(Float, Float), Float](zip2D(ix, iy), { case (a, b) => a * b })
    val iyy = computePointwiseGold(iy, { x: Float => x * x })
    val sxx = C2D.computeGold(h, w, ixx, C2D.binomialWeights2d)
    val sxy = C2D.computeGold(h, w, ixy, C2D.binomialWeights2d)
    val syy = C2D.computeGold(h, w, iyy, C2D.binomialWeights2d)
    computePointwiseGold[(Float, (Float, Float)), Float](zip2D(sxx, zip2D(sxy, syy)), { case (sxx, (sxy, syy)) =>
      val det = sxx * syy - sxy * sxy
      val trace = sxx + syy
      det - kappa * trace * trace
    })
  }
}