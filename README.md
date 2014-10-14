Stateless Future For Akka
=========================

<div align="right"><a href="https://travis-ci.org/qifun/stateless-future-akka"><img alt="Build Status" src="https://travis-ci.org/qifun/stateless-future-akka.png?branch=master"/></a></div>

**stateless-future-akka** allows you to build control flow for Akka actor in the native Scala syntax. `stateless-future-akka` bases on [stateless-future](https://github.com/Atry/stateless-future), which is a better future system than `scala.concurrent.Future`.

## Usage

### Step 1: Mix-in `FutureFactory` with your `Actor`

    import com.qifun.statelessFuture.akka.FutureFactory
    import akka.actor._
    class YourActor extends Actor with FutureFactory {
    }

### Step 2: Implement the `receive` method from a `Future` block

    import com.qifun.statelessFuture.akka.FutureFactory
    import akka.actor._
    class YourActor extends Actor with FutureFactory {
      override def receive = Future {
      	???
      }
    }

### Step 3: Receive and send message in the `Future` block

There is a magic method `nextMessage.await` that receive the next message from the actor's mail box. Unlike a normal `Actor.Receive`, You are able to receive multiple message sequentially in a `Future` block:

    import com.qifun.statelessFuture.akka.FutureFactory
    import akka.actor._
    class YourActor extends Actor with FutureFactory {
      override def receive = Future {
        while (true) {
          val message1 = nextMessage.await
          val message2 = nextMessage.await
          sender ! s"You have sent me $message1 and $message2"
        }
        throw new IllegalStateException("Unreachable code!")
      }
    }

Note that the `Future` block for `receive` must receive all the message until the actor stops. In fact, the `def receive = Future { ??? }` is a shortcut of `def receive = FutureFactory.receiveForever(Future[Nothing] { ??? })`.

## Another example

This example creates an actor that concantenates arbitrary number of strings.

    import com.qifun.statelessFuture.akka.FutureFactory
    import akka.actor._
    import scala.concurrent.duration._
    
    class ConcatenationActor extends Actor with FutureFactory {
      def nextInt: Future[Int] = Future {
        nextMessage.await.toString.toInt
      }
      override def receive = Future {
        while (true) {
          // Should behave the same as:
          // val numberOfSubstrings = nextMessage.await.toString.toInt
          val numberOfSubstrings = nextInt.await
          var i = 0
          val sb = new StringBuilder
          while (i < numberOfSubstrings) {
            sb ++= nextMessage.await.toString
            i += 1
          }
          val result = sb.toString
          println(result)
          sender ! result
        }
        throw new IllegalStateException("Unreachable code!")
      }
    }
    
    object ConcatenationActor {
      def main(arguments: Array[String]) {
        val system = ActorSystem("helloworld")
        val concatenationActor = system.actorOf(Props[ConcatenationActor], "concatenationActor")
        val inbox = Inbox.create(system)
        inbox.send(concatenationActor, "4")
        inbox.send(concatenationActor, "Hello")
        inbox.send(concatenationActor, ", ")
        inbox.send(concatenationActor, "world")
        inbox.send(concatenationActor, "!")
        assert(inbox.receive(5.seconds) == "Hello, world!")
        inbox.send(concatenationActor, "2")
        inbox.send(concatenationActor, "Hello, ")
        inbox.send(concatenationActor, "world, again!")
        assert(inbox.receive(5.seconds) == "Hello, world, again!")
      }
    }

## Installation

Put these lines in your `build.sbt` if you use [Sbt](http://www.scala-sbt.org/):

    libraryDependencies += "com.qifun" %% "stateless-future-akka" % "0.1.1"

`stateless-future-akka` should work with Scala 2.10.3, 2.10.4, or 2.11.0.

## Links

* [The API document](http://central.maven.org/maven2/com/qifun/stateless-future-akka_2.11/0.1.1/stateless-future-akka_2.11-0.1.1-javadoc.jar)
* [Tests and examples](https://github.com/Atry/stateless-future-test/tree/2.10.x/test/src/test/scala/com/qifun/statelessFuture/test/run)
