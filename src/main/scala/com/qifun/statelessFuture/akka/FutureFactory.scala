/*
 * stateless-future-akka
 * Copyright 2014 深圳岂凡网络有限公司 (Shenzhen QiFun Network Corp., LTD)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.qifun.statelessFuture.akka

import scala.reflect.macros.Context
import scala.util.control.Exception.Catcher
import scala.util.control.TailCalls.TailRec
import scala.util.control.TailCalls.done

import com.qifun.statelessFuture.ANormalForm
import com.qifun.statelessFuture.Awaitable

import akka.actor.Actor
import akka.actor.ActorContext

object FutureFactory {

  /**
   * Used internally only.
   */
  final def futureMacro(c: Context)(block: c.Expr[Any]): c.Expr[Nothing] = {
    import c.universe._
    val FutureName = newTermName("Future")
    val Apply(TypeApply(Select(thisTree, FutureName), List(t)), _) = c.macroApplication
    ANormalForm.applyMacroWithType(c)(block, t, Select(thisTree, newTypeName("TailRecResult")))
  }

  import scala.language.implicitConversions

  /**
   * Implicitly converts a `Future[Nothing]` to an `akka.actor.Actor.Receive`.
   * @note This method is usually used to implement `Actor`'s `receive` method.
   * @param future The future that takes control forever.
   * @return The entry that receive the first message.
   * @example override def receive = newContinualReceive(myFuture)
   */
  @inline
  implicit final def receiveForever(future: FutureFactory#Future[Nothing]): Actor.Receive = {
    future.onComplete { u =>
      done(null)
    }(PartialFunction.empty).result match {
      case r: FutureFactory#ReceiveAll => r
      case _ => throw new IllegalStateException
    }
  }

  /**
   * Converts a `Future[Unit] to an `akka.actor.Actor.Receive`.
   * @note This method usually works with `context.become`.
   * @param future The future that takes control until it returns.
   * When `future` returns, `context.unbecome` will be called, in order to return to the previous `akka.actor.Actor.Receive`.
   * @return `None` if `future` has exited before invoking `nextMessage.await`, `Some` if `future` has invoked `nextMessage.await`.
   * @example receiveUntilReturn(myFuture) foreach { context.become(_, false) }
   */
  @inline
  final def receiveUntilReturn(future: FutureFactory#Future[Unit]): Option[Actor.Receive] = {
    future.onComplete { u =>
      done(Unbecome)
    }(PartialFunction.empty).result match {
      case r: FutureFactory#ReceiveAll => Some(r)
      case Unbecome => None
    }
  }

  /**
   * Used for type check, to distinguish futures for different actors.
   */
  sealed abstract class TailRecResult[+FutureFactory] {
    def continue(context: ActorContext): Unit
  }

  private final object Unbecome extends TailRecResult[Nothing] {
    override final def continue(context: ActorContext): Unit = {
      context.unbecome()
    }
  }

}

/**
 * A factory that produces stateless futures for an actor.
 */
trait FutureFactory {

  /**
   * The context of an actor, which is most likely implemented by `akka.actor.Actor` when mixing-in [[FutureFactory]] with `akka.actor.Actor`
   */
  implicit def context: ActorContext

  /**
   * The unique type of future for an actor.
   * @note Unlike [[com.qifun.statelessFuture.Future]], futures from different [[FutureFactory]] instances are distinct types.
   */
  type Future[+AwaitResult] = Awaitable[AwaitResult, TailRecResult]

  import scala.language.experimental.macros

  /**
   * The magic constructor of [[Future]], which allows the magic `await` methods in the `block`.
   * @note You are able to `await` a future in another future only if the two futures are from the same [[FutureFactory]] instance.
   * @usecase def Future[AwaitResult](block: AwaitResult): Future[AwaitResult] = ???
   */
  final def Future[AwaitResult](block: AwaitResult): Awaitable.Stateless[AwaitResult, TailRecResult] = macro FutureFactory.futureMacro

  /**
   * Receive the next message from the mail box of the actor.
   * @usecase def nextMessage: Future[Any] = ???
   */
  final def nextMessage: Awaitable.Stateless[Any, TailRecResult] = new Awaitable.Stateless[Any, TailRecResult] {
    override final def onComplete(rest: Any => TailRec[TailRecResult])(implicit catcher: Catcher[TailRec[TailRecResult]]): TailRec[ReceiveAll] = {
      done(new ReceiveAll(rest))
    }
  }

  /**
   * Used for type check, to distinguish futures for different actors.
   */
  type TailRecResult = FutureFactory.TailRecResult[this.type]

  private final class ReceiveAll private[FutureFactory] (rest: Any => TailRec[TailRecResult]) extends TailRecResult with Actor.Receive {
    override final def continue(context: ActorContext): Unit = {
      context.become(this)
    }
    override final def isDefinedAt(any: Any) = true
    override final def apply(any: Any): Unit = {
      rest(any).result.continue(context)
    }
  }

}
