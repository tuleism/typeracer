package typeracer

import akka.actor.ActorRef
import spray.json.DefaultJsonProtocol

case class Player(id: String, name: String)

case class RequestUserRepr(player: Player)

case class RoomExpired(roomRef: ActorRef)

sealed trait GameOutputEvent

case class StateUpdate(state: Map[Player, PlayerState]) extends GameOutputEvent

case class ProblemStatement(quote: String, timeLeft: Int) extends GameOutputEvent

sealed trait GameInputEvent

case class PlayerConnected(player: Player, ref: ActorRef) extends GameInputEvent

case class PlayerAction(input: PlayerInput) extends GameInputEvent

case class PlayerDisconnected(player: Player)

sealed trait PlayerInput

case object NewGame extends PlayerInput

case class AtPosition(player: Player, position: Int) extends PlayerInput

case class RoomRequest(player: Player, ref: ActorRef)

case class AssignedRoom(ref: ActorRef)

trait GameEventMarshalling extends DefaultJsonProtocol {

  import spray.json._

  implicit val playerFormat = jsonFormat2(Player)
  implicit val playerStateFormat = jsonFormat3(PlayerState)
  implicit val problemStatementFormat = jsonFormat2(ProblemStatement)

  implicit object playerStatePairWriter extends RootJsonWriter[(Player, PlayerState)] {
    def write(obj: (Player, PlayerState)) =
      JsObject(
        "player" -> obj._1.toJson,
        "state" -> obj._2.toJson
      )
  }

}
