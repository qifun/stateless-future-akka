Stateless Future For Akka
=========================

## Usage

    import com.qifun.statelessFuture.akka.FutureFactory
    import akka.actor._
    import scala.concurrent.duration._
    
    class ConcatenationActor extends Actor with FutureFactory {
      def nextInt = Future {
        nextMessage.await.toString.toInt
      }
      def receive = Future {
        while (true) {
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

    libraryDependencies += "com.qifun" %% "stateless-future-akka" % "0.1.0"

`stateless-future-akka` should work with Scala 2.10.3, 2.10.4, or 2.11.0.
