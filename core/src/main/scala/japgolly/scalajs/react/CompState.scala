package japgolly.scalajs.react

import scala.concurrent.{Future, Promise}
import CompScope._

object CompState {

  /**
   * Read-and-write access to a component's state (or a subset).
   */
  type Access[S] = ReadCallbackWriteCallbackOps[S]

  /**
   * Read-and-write access to a component's state (or a subset).
   *
   * (RD = Read Direct)
   */
  type AccessRD[S] = ReadDirectWriteCallbackOps[S]

  /**
   * Read-and-write access to a component's state (or a subset).
   *
   * (D = Direct)
   */
  type AccessD[S] = ReadDirectWriteDirectOps[S]

  /**
   * Write access to a component's state (or a subset).
   */
  type WriteAccess[S] = WriteCallbackOps[S]

  /**
   * Write access to a component's state (or a subset).
   *
   * (D = Direct)
   */
  type WriteAccessD[S] = WriteDirectOps[S]

  /**
   * Read access to a component's state (or a subset).
   */
  type ReadAccess[S] = ReadCallbackOps[S]

  /**
   * Read access to a component's state (or a subset).
   *
   * (D = Direct)
   */
  type ReadAccessD[S] = ReadDirectOps[S]


  // ===================================================================================================================
  // Accessor
  // ===================================================================================================================

  sealed abstract class Accessor[$$, S] {
    final type $ = $$
    def state   ($: $): S
    def setState($: $)(s: S, cb: Callback): Unit
    def modState($: $)(f: S => S, cb: Callback): Unit
    def zoom[T](f: S => T)(g: (S, T) => S): Accessor[$, T]
  }
  object RootAccessor {
    private[this] val instance = new RootAccessor[Any]
    def apply[S] = instance.asInstanceOf[RootAccessor[S]]
  }
  class RootAccessor[S] extends Accessor[CanSetState[S], S] {
    override def state   ($: $)                          = $._state.v
    override def setState($: $)(s: S, cb: Callback)      = $._setState(WrapObj(s), cb.toJsCallback)
    override def modState($: $)(f: S => S, cb: Callback) = $._modState((s: WrapObj[S]) => WrapObj(f(s.v)), cb.toJsCallback)
    def zoom[T](f: S => T)(g: (S, T) => S): Accessor[$, T] =
      new ZoomAccessor[S, T](this, f, g)
  }
  class ZoomAccessor[S, T](parent: RootAccessor[S], get: S => T, set: (S, T) => S) extends Accessor[CanSetState[S], T] {
    override def state   ($: $)                          = get(parent state $)
    override def setState($: $)(t: T, cb: Callback)      = parent.modState($)(s => set(s, t), cb)
    override def modState($: $)(f: T => T, cb: Callback) = parent.modState($)(s => set(s, f(get(s))), cb)
    def zoom[U](f: T => U)(g: (T, U) => T): Accessor[$, U] =
      new ZoomAccessor[S, U](parent, f compose get, (s, u) => set(s, g(get(s), u)))
  }

  // ===================================================================================================================
  // Ops traits
  // ===================================================================================================================

  trait BaseOps[S] {
    type This[s] <: BaseOps[s]
    type WriteFutureAccess[s] <: WriteFutureOps[s]
    protected type $$
    protected val $: $$
    protected val a: Accessor[$$, S]

    def accessCB: Access[S]
    def accessDirect: AccessD[S]
    def future: WriteFutureAccess[S]
  }

  trait ZoomOps[S] extends BaseOps[S] {
    type This[s] <: ZoomOps[s]
    protected def changeAccessor[T](a2: Accessor[$$, T]): This[T]
    final def zoom[T](f: S => T)(g: (S, T) => S): This[T] =
      changeAccessor(a.zoom(f)(g))
  }

  trait ReadDirectOps[S] extends ZoomOps[S] {
    type This[T] <: ReadDirectOps[T]
    final def state: S = a state $
  }
  trait ReadCallbackOps[S] extends ZoomOps[S] {
    type This[T] <: ReadCallbackOps[T]
    final def state: CallbackTo[S] = CallbackTo(a state $)
  }

  trait WriteOps[S] extends BaseOps[S] {
    type This[T] <: WriteOps[T]
    type WriteResult
    def setState  (s: S            , cb: Callback = Callback.empty): WriteResult
    def modState  (f: S => S       , cb: Callback = Callback.empty): WriteResult
    def setStateCB(s: CallbackTo[S], cb: Callback = Callback.empty): WriteResult

    final def modStateCB(f: S => CallbackTo[S], cb: Callback = Callback.empty): WriteResult =
      modState(f andThen (_.runNow()), cb)

    final def _setState[I](f: I => S, cb: Callback = Callback.empty): I => WriteResult =
      i => setState(f(i), cb)

    final def _modState[I](f: I => S => S, cb: Callback = Callback.empty): I => WriteResult =
      i => modState(f(i), cb)

    final def _setStateCB[I](f: I => CallbackTo[S], cb: Callback = Callback.empty): I => WriteResult =
      i => setStateCB(f(i), cb)

    final def _modStateCB[I](f: I => S => CallbackTo[S], cb: Callback = Callback.empty): I => WriteResult =
      i => modStateCB(f(i), cb)
  }

  trait WriteDirectOps[S] extends WriteOps[S] with ZoomOps[S] {
    override type This[T] <: WriteDirectOps[T]
    final override type WriteFutureAccess[S] = WriteDirectFutureOps[S]
    final override type WriteResult = Unit
    final override def setState  (s: S            , cb: Callback = Callback.empty): Unit = a.setState($)(s, cb)
    final override def modState  (f: S => S       , cb: Callback = Callback.empty): Unit = a.modState($)(f, cb)
    final override def setStateCB(s: CallbackTo[S], cb: Callback = Callback.empty): Unit = setState(s.runNow(), cb)
  }
  trait WriteCallbackOps[S] extends WriteOps[S] with ZoomOps[S] {
    override type This[T] <: WriteCallbackOps[T]
    final override type WriteFutureAccess[S] = WriteCallbackFutureOps[S]
    final override type WriteResult = Callback
    final override def setState  (s: S            , cb: Callback = Callback.empty): Callback = CallbackTo(a.setState($)(s, cb))
    final override def modState  (f: S => S       , cb: Callback = Callback.empty): Callback = CallbackTo(a.modState($)(f, cb))
    final override def setStateCB(s: CallbackTo[S], cb: Callback = Callback.empty): Callback = s >>= (setState(_, cb))
  }

  trait ReadDirectWriteDirectOps[S] extends ReadDirectOps[S] with WriteDirectOps[S] {
    override final type This[T] = ReadDirectWriteDirectOps[T]
  }
  trait ReadDirectWriteCallbackOps[S] extends ReadDirectOps[S] with WriteCallbackOps[S] {
    override final type This[T] = ReadDirectWriteCallbackOps[T]
  }
  trait ReadCallbackWriteCallbackOps[S] extends ReadCallbackOps[S] with WriteCallbackOps[S] {
    override final type This[T] = ReadCallbackWriteCallbackOps[T]
  }

  trait WriteFutureOps[S] extends WriteOps[S] {
    type This[T] <: WriteFutureOps[T]
    protected val underlying: WriteOps[S]
    protected def make(cb: Callback, call: (underlying.type, Callback) => underlying.WriteResult): WriteResult
    final override def setState  (s: S            , cb: Callback = Callback.empty) = make(cb, _.setState(s, _))
    final override def modState  (f: S => S       , cb: Callback = Callback.empty) = make(cb, _.modState(f, _))
    final override def setStateCB(s: CallbackTo[S], cb: Callback = Callback.empty) = make(cb, _.setStateCB(s, _))
  }

  trait WriteCallbackFutureOps[S] extends WriteFutureOps[S] {
    override final type This[T] = WriteCallbackFutureOps[T]
    final override type WriteFutureAccess[T] = WriteCallbackFutureOps[T]
    override final type WriteResult = CallbackTo[Future[Unit]]
    override protected val underlying: WriteCallbackOps[S]
    override protected def make(cb: Callback, call: (underlying.type, Callback) => underlying.WriteResult): WriteResult =
      CallbackTo(makeFuture(cb, call(underlying, _).runNow()))
  }

  trait WriteDirectFutureOps[S] extends WriteFutureOps[S] {
    override final type This[T] = WriteDirectFutureOps[T]
    final override type WriteFutureAccess[T] = WriteDirectFutureOps[T]
    override final type WriteResult = Future[Unit]
    override protected val underlying: WriteDirectOps[S]
    override protected def make(cb: Callback, call: (underlying.type, Callback) => underlying.WriteResult): WriteResult =
      makeFuture(cb, call(underlying, _))
  }

  private[CompState] def makeFuture(cb: Callback, call: Callback => Unit): Future[Unit] = {
    val p = Promise[Unit]()
    val cb2 = cb.attempt.map {
      case Right(a) => p.success(a); ()
      case Left (e) => p.failure(e); ()
    }
    call(cb2)
    p.future
  }


  // ===================================================================================================================
  // Ops impls
  // ===================================================================================================================

  private[react] final class ReadDirectWriteDirect[$, S](override protected val $: $, override protected val a: Accessor[$, S])
    extends ReadDirectWriteDirectOps[S] {
    override protected type $$ = $
    override protected def changeAccessor[T](a2: Accessor[$, T]) = new ReadDirectWriteDirect($, a2)
    override def accessCB     = new ReadCallbackWriteCallback($, a)
    override def accessDirect = this
    override def future       = new WriteDirectFuture($, a, this)
  }

  private[react] final class ReadDirectWriteCallback[$, S](override protected val $: $, override protected val a: Accessor[$, S])
    extends ReadDirectWriteCallbackOps[S] {
    override protected type $$ = $
    override protected def changeAccessor[T](a2: Accessor[$, T]) = new ReadDirectWriteCallback($, a2)
    override def accessCB     = new ReadCallbackWriteCallback($, a)
    override def accessDirect = new ReadDirectWriteDirect($, a)
    override def future       = new WriteCallbackFuture($, a, this)
  }

  private[react] final class ReadCallbackWriteCallback[$, S](override protected val $: $, override protected val a: Accessor[$, S])
    extends ReadCallbackWriteCallbackOps[S] {
    override protected type $$ = $
    override protected def changeAccessor[T](a2: Accessor[$, T]) = new ReadCallbackWriteCallback($, a2)
    override def accessCB     = this
    override def accessDirect = new ReadDirectWriteDirect($, a)
    override def future       = new WriteCallbackFuture($, a, this)
  }

  private[react] final class WriteDirectFuture[$, S](override protected val $: $,
                                                     override protected val a: Accessor[$, S],
                                                     override protected val underlying: WriteDirectOps[S])
    extends WriteDirectFutureOps[S] {
    override protected type $$ = $
    override def accessCB     = new ReadCallbackWriteCallback($, a)
    override def accessDirect = new ReadDirectWriteDirect($, a)
    override def future       = this
  }

  private[react] final class WriteCallbackFuture[$, S](override protected val $: $,
                                                       override protected val a: Accessor[$, S],
                                                       override protected val underlying: WriteCallbackOps[S])
    extends WriteCallbackFutureOps[S] {
    override protected type $$ = $
    override def accessCB     = new ReadCallbackWriteCallback($, a)
    override def accessDirect = new ReadDirectWriteDirect($, a)
    override def future       = this
  }
}
