package japgolly.scalajs.react

import java.util.concurrent.atomic.AtomicReference
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag
import scala.scalajs.js
import scalaz.Maybe
import vdom.all._
import utest._
import CompScope._

object TestUtil {

  def assertRender(comp: ReactElement, expected: String): Unit = {
    val rendered: String = ReactDOMServer.renderToStaticMarkup(comp)
    assert(rendered == expected)
  }

  implicit class ReactComponentUAS(private val c: ReactElement) extends AnyVal {
    def shouldRender(expected: String) = assertRender(c, expected)
  }

  def collector1[A](f: DuringCallbackU[_, _, _] => A) =
    ReactComponentB[AtomicReference[Option[A]]]("C₁").stateless
      .render(T => { T.props set Some(f(T)); div ("x") }).build

  def collector1C[A](f: PropsChildren => A) =
    collector1[A](t => f(t.propsChildren))

  def run1[A](C: ReactComponentC.ReqProps[AtomicReference[Option[A]], _, _, _])
             (f: AtomicReference[Option[A]] => ReactComponentU[AtomicReference[Option[A]], _, _, _]): A = {
    val a = new AtomicReference[Option[A]](None)
    ReactDOMServer renderToStaticMarkup f(a)
    a.get().get
  }

  def run1C[A](c: ReactComponentC.ReqProps[AtomicReference[Option[A]], _, _, _], children: ReactNode*): A =
    run1(c)(a => c(a, children: _*))

  def collectorN[A](f: (ListBuffer[A], DuringCallbackU[_, _, _]) => Unit) =
    ReactComponentB[ListBuffer[A]]("Cₙ").stateless
      .render(T => { f(T.props, T); div ("x") }).build

  def collectorNC[A](f: (ListBuffer[A], PropsChildren) => Unit) =
    collectorN[A]((l,t) => f(l, t.propsChildren))

  def runN[A](C: ReactComponentC.ReqProps[ListBuffer[A], _, _, _])
             (f: ListBuffer[A] => ReactComponentU[ListBuffer[A], _, _, _]): List[A] = {
    val l = new ListBuffer[A]
    ReactDOMServer renderToStaticMarkup f(l)
    l.result()
  }

  def runNC[A](c: ReactComponentC.ReqProps[ListBuffer[A], _, _, _], children: ReactNode*) =
    runN(c)(l => c(l, children: _*))

  implicit class AnyTestExt[A](private val v: A) extends AnyVal {

    // nice output in assertion macro
    def mustEqual(e: A): Unit = {
      val a = v
      assert(a == e)
    }

    def some: Option[A] = Some(v)
    def none: Option[A] = None

    def jsdef: js.UndefOr[A] = v
    def undef: js.UndefOr[A] = js.undefined

    def just    : Maybe[A] = Maybe.just(v)
    def maybeNot: Maybe[A] = Maybe.empty

    def matchesBy[B <: A : ClassTag](f: B => Boolean) = v match {
      case b: B => f(b)
      case _ => false
    }
  }

  def none[A]: Option[A] = None

  def assertContains(value: String, search: String, expect: Boolean = true): Unit =
    if (value.contains(search) != expect) {
      println(s"\nValue: $value\nSearch: $search\nExpect: $expect\n")
      assert(false)
    }

  def assertTypeMismatch(e: CompileError): Unit =
    assertContains(e.msg, "type mismatch")

  implicit class JsArrayTestExt[A](private val a: js.Array[A]) extends AnyVal {
    def sole(): A =
      a.length match {
        case 1 => a(0)
        case n => TestUtil2.fail(s"Expected an array with one element, found $n: ${a.mkString("[",",","]")}")
    }
  }

  // ===================================================================================================================
  object Inference {
    import scalaz.{Monad, ~>}

    def test[A] = new {
      def apply[B](f: A => B) = new {
        def expect[C](implicit ev: B =:= C): Unit = ()
        def expect_<[C](implicit ev: B <:< C): Unit = ()
      }
    }

    trait M[A]
    trait P
    trait S
    trait T
    trait A
    trait B
    type U = Unit
    @js.native trait N extends TopNode
    val c = null.asInstanceOf[ReactComponentM[Unit, S, Unit, N]]
    val bs = null.asInstanceOf[BackendScope[P, S]]

    def st_s(s: S, t: T): S = ???

    implicit val mMonad = null.asInstanceOf[Monad[M] with (M ~> CallbackTo)]
  }
}