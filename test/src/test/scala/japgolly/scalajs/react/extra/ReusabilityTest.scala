package japgolly.scalajs.react
package extra

import nyaya.gen._
import nyaya.prop._
import nyaya.test.PropTest._
import utest._

import vdom.prefix_<^._
import test._
import ScalazReact._
import TestUtil2._
import CompScope._
import CompState._

object ReusabilityTest extends TestSuite {

  object SampleComponent1 {
    case class Picture(id: Long, url: String, title: String)
    case class Props(name: String, age: Option[Int], pic: Picture)

    implicit val picReuse   = Reusability.by((_: Picture).id)
    implicit val propsReuse = Reusability.caseClass[Props]

    var renderCount = 0

    val component = ReactComponentB[Props]("Demo")
      .initialState_P(identity)
      .renderS { (_, *) =>
        renderCount += 1
        <.div(
          <.p("Name: ", *.name),
          <.p("Age: ", *.age.fold("Unknown")(_.toString)),
          <.img(^.src := *.pic.url, ^.title := *.pic.title))
      }
      .configure(Reusability.shouldComponentUpdate)
      .build
  }

  object SampleComponent2 {
    var outerRenderCount = 0
    var innerRenderCount = 0
    type M = Map[Int, String]

    val outerComponent = ReactComponentB[M]("Demo")
      .initialState_P(identity)
      .renderBackend[Backend]
      .build

    class Backend($: BackendScope[_, M]) {
      val updateUser = ReusableFn((id: Int, data: String) =>
        $.modState(_.updated(id, data)))
      def render(s: M) = {
        outerRenderCount += 1
        <.div(
          s.map { case (id, name) =>
            innerComponent.withKey(id)(InnerProps(name, updateUser(id)))
          }.toJsArray)
      }
    }

    case class InnerProps(name: String, update: String ~=> Callback)
    implicit val propsReuse = Reusability.caseClass[InnerProps]

    val innerComponent = ReactComponentB[InnerProps]("PersonEditor")
      .renderP { (_, p) =>
        innerRenderCount += 1
        <.input(
          ^.`type` := "text",
          ^.value := p.name,
          ^.onChange ==> ((e: ReactEventI) => p.update(e.target.value)))
      }
      .configure(Reusability.shouldComponentUpdate)
      .build
  }

  case class CC0()
  case class CC1(i: Int)
  case class CC2(i: Int, n: String)

  case class CCT0[A]()
  case class CCT1[A](i: A)
  case class CCT2[A](i: Int, n: A)

  val collectionData = {
    val a = Vector(3,1,2,3,2,1)
    (for (l <- 0 to a.length) yield a.combinations(l).toSet).reduce(_ ++ _)
  }
  def testCollection[F[_]](f: Vector[Int] => F[Int])(implicit r: Reusability[F[Int]]): Unit = {
    val d = collectionData.map(f)
    for {a <- d; b <- d}
      assertEq(r.test(a, b), a == b)
  }

  val tests = TestSuite {

    'caseClassMacro {
      def test[A](a: A, b: A, expect: Boolean)(implicit r: Reusability[A]) =
        assert(r.test(a, b) == expect)

      'cc0 {
        implicit val r = Reusability.caseClass[CC0]
        test(CC0(), CC0(), true)
      }
      'cc1 {
        implicit val r = Reusability.caseClass[CC1]
        test(CC1(2), CC1(2), true)
        test(CC1(2), CC1(3), false)
      }
      'cc2 {
        implicit val r = Reusability.caseClass[CC2]
        test(CC2(3,"a"), CC2(3,"a"), true)
        test(CC2(3,"a"), CC2(3,"b"), false)
        test(CC2(3,"a"), CC2(4,"a"), false)
      }

      'cct0 {
        implicit val r = Reusability.caseClass[CCT0[Int]]
        test(CCT0[Int](), CCT0[Int](), true)
      }
      'cct1 {
        implicit val r = Reusability.caseClass[CCT1[Int]]
        test(CCT1(2), CCT1(2), true)
        test(CCT1(2), CCT1(3), false)
      }
      'cct2 {
        implicit val r = Reusability.caseClass[CCT2[String]]
        test(CCT2(3,"a"), CCT2(3,"a"), true)
        test(CCT2(3,"a"), CCT2(3,"b"), false)
        test(CCT2(3,"a"), CCT2(4,"a"), false)
      }
    }

    'shouldComponentUpdate {
      'reusableState {
        import SampleComponent1._

        val pic1a = Picture(1, "asdf", "qer")
        val pic1b = Picture(1, "eqwrg", "seafr")
        val pic2  = Picture(2, "asdf", "qer")

        val c = ReactTestUtils renderIntoDocument component(Props("n", None, pic1a))
        def test(expectDelta: Int, s: Props): Unit = {
          val a = renderCount
          c.setState(s)
          assert(renderCount == a + expectDelta)
        }
        val (update,ignore) = (1,0)

        test(ignore, Props("n", None, pic1a))
        test(update, Props("!", None, pic1a))
        test(ignore, Props("!", None, pic1a))
        test(ignore, Props("!", None, pic1b))
        test(update, Props("!", None, pic2))
        test(ignore, Props("!", None, pic2))
        test(update, Props("!", Some(3), pic2))
        test(update, Props("!", Some(4), pic2))
        test(ignore, Props("!", Some(4), pic2))
        test(update, Props("!", Some(5), pic2))
      }

      'reusableProps {
        import SampleComponent2._
        val data1: M = Map(1 -> "One", 2 -> "Two", 3 -> "Three")
        val data2: M = Map(1 -> "One", 2 -> "Two", 3 -> "33333")
        val c = ReactTestUtils renderIntoDocument outerComponent(data1)
        assert(outerRenderCount == 1, innerRenderCount == 3)
        c.forceUpdate()
        assert(outerRenderCount == 2, innerRenderCount == 3)
        c.setState(data2)
        assert(outerRenderCount == 3, innerRenderCount == 4)
      }
    }

    'uuid {
      import java.util.UUID
      val value = UUID.randomUUID.toString

      assert(UUID.fromString(value) ~=~ UUID.fromString(value))
      assert(!(UUID.fromString(value) ~=~ UUID.randomUUID))
    }

    'jsDate {
      import scala.scalajs.js.Date
      val now = System.currentTimeMillis
      val date1 = new Date(now)
      val date2 = new Date(now)

      assert(date1 ~=~ date2)
      assert(!(date1 ~=~ new Date(now + 1)))
    }

    'javaDate {
      import java.util.Date
      val now = System.currentTimeMillis
      val date1 = new Date(now)
      val date2 = new Date(now)

      assert(date1 ~=~ date2)
      assert(!(date1 ~=~ new Date(now + 1)))
    }

    'doubleWithTolerance {
      implicit val r = Reusability.double(0.2)
      assert(1.2.toDouble ~=~ 1.0.toDouble)
      assert(0.8.toDouble ~=~ 1.0.toDouble)

      assert(!(0.7.toDouble ~=~ 1.0.toDouble))
      assert(!(1.3.toDouble ~=~ 1.0.toDouble))
    }

    'floatWithTolerance {
      implicit val r = Reusability.float(0.2f)
      assert(0.9f ~=~ 1.0f)
      assert(1.0f ~=~ 1.0f)
      assert(1.1f ~=~ 1.0f)

      assert(!(0.7f ~=~ 1.0f))
      assert(!(1.3f ~=~ 1.0f))
    }

    'option {
      def test(vs: Option[Boolean]*) =
        for {a <- vs; b <- vs}
          assert((a ~=~ b) == (a == b))
      test(None, Some(true), Some(false))
    }

    'vector - testCollection(_.toVector)
    'list   - testCollection(_.toList)
    'set    - testCollection(_.toSet)

    'map {
      val r = Reusability.map[Int, Int]
      val data = Gen.chooseInt(8).mapTo(Gen.chooseInt(8)).pair
      data mustSatisfy Prop.equal("Reusability matches equality")(r.test.tupled, i => i._1 == i._2)
    }

    'fns {
      type F1[A] = Int ~=> A
      type F2[A] = Int ~=> F1[A]
      type F3[A] = Int ~=> F2[A]

      def test1[A](f: F1[A], g: F1[A]): Unit = {
        assert(f ~=~ f)
        assert(f ~/~ g)
      }

      def test2[A](f: F2[A], g: F2[A]): Unit = {
        test1(f, g)
        assert(f(1) ~=~ f(1))
        assert(f(1) ~/~ f(2))
        assert(f(1) ~/~ g(1))
      }

      def test3[A](f: F3[A], g: F3[A]): Unit = {
        test2(f, g)
        assert(f(1)(2) ~=~ f(1)(2))
        assert(f(1)(2) ~/~ f(1)(3))
        assert(f(1)(2) ~/~ f(2)(2))
        assert(f(1)(2) ~/~ f(2)(1))
        assert(f(2)(1) ~=~ f(2)(1))
        assert(f(1)(2) ~/~ g(1)(2))
      }

      'fn1 {
        val f = ReusableFn((i: Int) => i + 1)
        val g = ReusableFn((i: Int) => i + 10)
        test1(f, g)
        assert(f(5) == 6)
      }

      'fn2 {
        val f = ReusableFn((a: Int, b: Int) => a + b)
        val g = ReusableFn((a: Int, b: Int) => a * b)
        test2(f, g)
        assert(f(1)(2) == 3)
      }

      'fn3 {
        val f = ReusableFn((a: Int, b: Int, c: Int) => a + b + c)
        val g = ReusableFn((a: Int, b: Int, c: Int) => a * b * c)
        test3(f, g)
        assert(f(1)(2)(3) == 6)
      }

      'overComponent {
        import TestUtil.Inference._
        test[DuringCallbackU[P, S, B]       ]($ => ReusableFn($).modState).expect[(S => S) ~=> Callback]
        test[DuringCallbackM[P, S, B, N]    ]($ => ReusableFn($).modState).expect[(S => S) ~=> Callback]
        test[BackendScope   [P, S]          ]($ => ReusableFn($).modState).expect[(S => S) ~=> Callback]
        test[ReadCallbackWriteCallbackOps[T]]($ => ReusableFn($).modState).expect[(T => T) ~=> Callback]
      }

      'endoOps {
        import TestUtil.Inference._
        case class Counter(count: Int) {
          def add(i: Int): Counter = copy(count = count + i)
        }
        test[BackendScope[P, S]          ]($ => ReusableFn($).modState.endoZoom(st_s)      ).expect[T ~=> Callback]
        test[BackendScope[P, Counter]    ]($ => ReusableFn($).modState.endoCall(_.add)     ).expect[Int ~=> Callback]
        test[BackendScope[P, Map[Int, S]]]($ => ReusableFn($).modState.endoCall2(_.updated)).expect[Int ~=> (S ~=> Callback)]
      }

      'byName {
        var state = 10
        val fn = ReusableFn.byName((_: Int) + state)
        assert(fn(2) == 12)
        state = 20
        assert(fn(2) == 22)
      }

      'renderComponent {
        import SampleComponent1._
        val f = ReusableFn.renderComponent(component)
        val g: Props => ReactElement = f
        ()
      }

      'fnA {
        val f = ReusableFn((a: Int) => a + a)
        val g = ReusableFn((a: Int) => a * a)
        val f3 = f.fnA(3)
        assert(f3 ~=~ f.fnA(3))
        assert(f3 ~/~ f.fnA(2))
        assert(f3 ~/~ g.fnA(3))
        assert(f3() == 6)
      }
    }

  }
}
