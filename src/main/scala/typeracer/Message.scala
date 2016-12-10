package typeracer

import akka.actor.ActorRef

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
