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

/**
 * Used internally only.
 */
object FutureFactory {
  final def behaviorMacro(c: Context)(block: c.Expr[Any]): c.Expr[Nothing] = {
    import c.universe._
    val FutureName = newTermName("Future")
    val Apply(TypeApply(Select(thisTree, FutureName), List(t)), _) = c.macroApplication
    ANormalForm.applyMacroWithType(c)(block, t, Select(thisTree, newTypeName("TailRecResult")), AppliedTypeTree(Select(thisTree, newTypeName("Future")), List(t)))
  }
}

/**
 * A factory that creates stateless futures for an actor.
 */
trait FutureFactory {

  /**
   * The context of an actor, which is mostly implemented by `akka.actor.Actor` when mixing-in [[FutureFactory]] with `akka.actor.Actor`
   */
  implicit def context: ActorContext

  /**
   * The unique type of future for an actor.
   * @note Unlike [[com.qifun.statelessFuture.Future]], futures from different [[FutureFactory]] instances are distinct types.
   */
  type Future[+AwaitResult] = Awaitable[AwaitResult, TailRecResult] {
    def onComplete(rest: Any => TailRec[TailRecResult])(implicit catcher: Catcher[TailRec[TailRecResult]]): TailRec[ReceiveAll]
  }

  import scala.language.experimental.macros

  /**
   * The magic constructor of [[Future]], which allows the magic `await` methods in the `block`.
   * @note You are able to `await` a future in another future only if the two futures are from the same [[FutureFactory]] instance.
   */
  final def Future[AwaitResult](block: AwaitResult): Future[AwaitResult] = macro FutureFactory.behaviorMacro

  /**
   * Receive the next message from mail box of the actor.
   */
  final def nextMessage: Future[Any] = new Awaitable.Stateless[Any, TailRecResult] {
    override final def onComplete(rest: Any => TailRec[TailRecResult])(implicit catcher: Catcher[TailRec[TailRecResult]]): TailRec[ReceiveAll] = {
      done(new ReceiveAll(rest))
    }
  }

  import scala.language.implicitConversions

  /**
   * The implicit converter from [[Future]] to `akka.actor.Actor.Receive`.
   */
  @inline
  implicit final def futureToReceive(future: Future[Any]): Actor.Receive = {
    future.onComplete { u =>
      done(new TailRecResult {
        override final def continue(): Unit = {
          context.unbecome()
        }
      })
    }(PartialFunction.empty).result
  }

  /**
   * Used internally only.
   */
  sealed abstract class TailRecResult {
    def continue(): Unit
  }

  private[FutureFactory] final class ReceiveAll private[FutureFactory] (rest: Any => TailRec[TailRecResult]) extends TailRecResult with Actor.Receive {
    override final def continue(): Unit = {
      context.become(this)
    }
    override final def isDefinedAt(any: Any) = true
    override final def apply(any: Any): Unit = {
      rest(any).result.continue()
    }
  }

}
