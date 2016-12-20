package typeracer

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.ask
import akka.util.Timeout

class GameService(system: ActorSystem, timeout: Timeout) extends Service {
  def createGameMaster() = system.actorOf(GameMaster.props)

  implicit val requestTimeout = timeout
}

trait Service extends Directives with GameFlow {

  def createGameMaster(): ActorRef

  lazy val gameMaster: ActorRef = createGameMaster()

  implicit val requestTimeout: Timeout

  def route: Route =
    (get & parameter("name")) { name =>
      val player = Player(UUID.randomUUID.toString, name)
      val assignedRepr =
        gameMaster.ask(RequestUserRepr(player)).mapTo[ActorRef]
      onSuccess(assignedRepr) {
        repr => handleWebSocketMessages(flow(repr, player))
      }
    }

}
