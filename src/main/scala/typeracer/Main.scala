package typeracer

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.Config

import scala.io.StdIn
import scala.util.{Failure, Success}

object Main extends App with RequestTimeout {
  implicit val system = ActorSystem()

  import system.dispatcher

  implicit val materializer = ActorMaterializer()

  val config = system.settings.config
  val host = config.getString("http.host")
  val port = config.getInt("http.port")

  val service = new GameService(system, requestTimeout(config))

  val binding = Http().bindAndHandle(service.route, host, port)
  val log = Logging(system.eventStream, "type-racer")
  binding.onComplete {
    case Success(_) => log.info(s"Server is listening on port $port. ENTER to quit")
    case Failure(e) =>
      log.error(s"Failed with ${e.getMessage}")
      system.terminate()
  }

  StdIn.readLine()
  system.terminate()
}

trait RequestTimeout {

  import scala.concurrent.duration._

  def requestTimeout(config: Config): Timeout = {
    val t = config.getString("akka.http.server.request-timeout")
    val d = Duration(t)
    FiniteDuration(d.length, d.unit)
  }
}
