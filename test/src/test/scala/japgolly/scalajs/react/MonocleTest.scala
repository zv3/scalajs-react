package japgolly.scalajs.react

import monocle._
import monocle.macros.Lenses
import utest._
import React._
import ScalazReact._
import MonocleReact._
import CompScope._
import CompState._

object MonocleTest extends TestSuite {

  @Lenses case class Poly[A](oa: Option[A])

  val tests = TestSuite {

    'inference {
      import TestUtil.Inference._
      val lensST: Lens[S, T] = null
      val lensTS: Lens[T, S] = null

      'zoomL {
        'DuringCallbackU - test[DuringCallbackU[P, S, U]   ](_ zoomL lensST).expect[ReadDirectWriteCallbackOps[T]]
        'DuringCallbackM - test[DuringCallbackM[P, S, U, N]](_ zoomL lensST).expect[ReadDirectWriteCallbackOps[T]]
        'BackendScope    - test[BackendScope   [P, S]      ](_ zoomL lensST).expect[ReadCallbackWriteCallbackOps[T]]
        'ReactComponentM - test[ReactComponentM[P, S, U, N]](_ zoomL lensST).expect[ReadDirectWriteDirectOps[T]]
        'ReactS          - test[ReactST[M, S, A]           ](_ zoomL lensTS).expect[ReactST[M, T, A]]
      }

      "compState.zoomL" - {
        'Access      - test[Access     [S]](_ zoomL lensST).expect[Access     [T]]
        'AccessD     - test[AccessD    [S]](_ zoomL lensST).expect[AccessD    [T]]
        'AccessRD    - test[AccessRD   [S]](_ zoomL lensST).expect[AccessRD   [T]]
        'WriteAccess - test[WriteAccess[S]](_ zoomL lensST).expect[WriteAccess[T]]
      }

      '_setStateL {
        'DuringCallbackU - test[DuringCallbackU[P, S, U]   ](_ _setStateL lensST).expect[T => Callback]
        'DuringCallbackM - test[DuringCallbackM[P, S, U, N]](_ _setStateL lensST).expect[T => Callback]
        'BackendScope    - test[BackendScope   [P, S]      ](_ _setStateL lensST).expect[T => Callback]
        'ReactComponentM - test[ReactComponentM[P, S, U, N]](_ _setStateL lensST).expect[T => Unit]
      }

      'poly {
        'zoomL      - test[BackendScope[P, Poly[S]]](_ zoomL      Poly.oa[S]).expect[ReadCallbackWriteCallbackOps[Option[S]]]
        '_setStateL - test[BackendScope[P, Poly[S]]](_ _setStateL Poly.oa[S]).expect[Option[S] => Callback]
      }

    }
  }
}