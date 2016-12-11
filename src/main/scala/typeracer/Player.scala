package typeracer

import akka.actor.{ActorRef, FSM, Props}

sealed trait PlayerReprState

case object ReprWaitingForPlayer extends PlayerReprState

case object ReprIdling extends PlayerReprState

case object ReprWaitingForRoom extends PlayerReprState

case object ReprPlaying extends PlayerReprState

case class PlayerReprData(info: Option[PlayerConnected], currentRoom: Option[ActorRef])

object PlayerRepr {
  def props(gameMaster: ActorRef): Props = Props(classOf[PlayerRepr], gameMaster)
}

class PlayerRepr(gameMaster: ActorRef) extends FSM[PlayerReprState, PlayerReprData] {

  startWith(ReprWaitingForPlayer, PlayerReprData(None, None))

  when(ReprWaitingForPlayer) {
    case Event(info: PlayerConnected, _) =>
      self ! PlayerAction(NewGame)
      goto(ReprIdling) using PlayerReprData(Some(info), None)
  }

  when(ReprIdling) {
    case Event(PlayerAction(NewGame), data) =>
      data.info foreach { info =>
        gameMaster ! RoomRequest(info.player, info.ref)
      }
      goto(ReprWaitingForRoom) using data.copy(currentRoom = None)
  }

  when(ReprWaitingForRoom) {
    case Event(AssignedRoom(roomRef), data) =>
      goto(ReprPlaying) using data.copy(currentRoom = Some(roomRef))
  }

  when(ReprPlaying) {
    case Event(PlayerAction(msg: AtPosition), data) =>
      data.currentRoom foreach (_ ! msg)
      stay

    case Event(PlayerAction(NewGame), data) =>
      data.info foreach { info =>
        gameMaster ! RoomRequest(info.player, info.ref)
        data.currentRoom foreach (_ ! PlayerDisconnected(info.player))
      }
      goto(ReprWaitingForRoom) using data.copy(currentRoom = None)
  }

  whenUnhandled {
    case Event(PlayerDisconnected(_), data) =>
      for {
        room <- data.currentRoom
        info <- data.info
      } yield
        room ! PlayerDisconnected(info.player)

      stop()
  }
}
