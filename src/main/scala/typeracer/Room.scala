package typeracer

import akka.actor.{ActorRef, FSM, Props}

import scala.concurrent.duration._

sealed trait RoomState

case object WaitingForPlayers extends RoomState

case object RacingInProgress extends RoomState

case class RoomData(countdown: Option[Int],
                    startTime: Option[Long],
                    pendingRefs: Set[ActorRef],
                    playerRefs: Map[Player, ActorRef],
                    playerStates: Map[Player, PlayerState])

case class PlayerState(position: Int, rank: Option[Int], wpm: Int)

object Room {

  val readyDuration = 7

  val gameDuration = 120

  def props(quote: String): Props = Props(classOf[Room], quote)

}

class Room(val quote: String) extends FSM[RoomState, RoomData] {

  case object NotifyPlayer

  val notifyTimer: String = "notifyPendingPlayers"

  val goal: Int = quote.split(" ").length

  startWith(WaitingForPlayers, RoomData(None, None, Set.empty, Map.empty, Map.empty))
  setTimer(notifyTimer, NotifyPlayer, 1.second, repeat = true)

  def broadcastState(data: RoomData): Unit =
    data.playerRefs.values.foreach(_ ! StateUpdate(data.playerStates))

  def currentTime: Long = System.currentTimeMillis()

  def calculateSpeed(position: Int, startTime: Long): Int = {
    val secondsSince: Double = (currentTime - startTime).toDouble / 1000
    val ratio = (math rint secondsSince * 10) / 10
    (position * 60 / ratio).toInt
  }

  when(WaitingForPlayers) {
    case Event(PlayerConnected(player, ref), data) =>
      val newData = data.copy(
        pendingRefs = data.pendingRefs + ref,
        playerRefs = data.playerRefs + (player -> ref),
        playerStates = data.playerStates + (player -> PlayerState(0, None, 0))
      )
      broadcastState(newData)
      stay using newData

    case Event(NotifyPlayer, data) =>
      // if there are more than 1 player, start countdown
      val initCountdown =
        if (data.pendingRefs.size > 1) Some(Room.readyDuration)
        else None
      val newCountdown = data.countdown.fold(initCountdown) {
        timeLeft => Some(timeLeft - 1)
      }
      // if there is a countdown, we send ProblemStatement to pending players
      newCountdown.foreach {
        timeLeft => data.pendingRefs foreach (_ ! ProblemStatement(quote, timeLeft))
      }
      // if there is a countdown, we remove pending players from state
      val newData = newCountdown.fold(data) {
        timeLeft =>
          data.copy(
            countdown = Some(timeLeft),
            pendingRefs = Set.empty
          )
      }

      //stop accepting new players when countdown is 3
      newCountdown.filter(_ == 3).foreach {
        _ => context.parent ! RoomExpired(self)
      }

      // stay in current state unless countdown is 0
      newCountdown.flatMap {
        timeLeft => if (timeLeft == 0) Some(0) else None
      }.fold(stay using newData) {
        _ => goto(RacingInProgress) using newData.copy(startTime = Some(currentTime))
      }
  }

  when(RacingInProgress, stateTimeout = Room.gameDuration.seconds) {
    case Event(AtPosition(player, position), data) =>
      val updatedPosition = for {
        starTime <- data.startTime
        oldPlayerState <- data.playerStates.get(player)
      } yield oldPlayerState.copy(
        position = position,
        wpm = calculateSpeed(position, starTime)
      )

      val newData = updatedPosition.map { s =>
        if (position < goal) s
        else {
          val noFinished = data.playerStates.values.count(_.rank.isDefined)
          s.copy(rank = Some(noFinished + 1))
        }
      }.map { newPlayerState =>
        data.copy(playerStates = data.playerStates updated(player, newPlayerState))
      }

      newData foreach (broadcastState(_))
      stay using newData.getOrElse(data)

    case Event(StateTimeout, _) =>
      stop()
  }

  onTransition {
    case WaitingForPlayers -> RacingInProgress => cancelTimer(notifyTimer)
  }

  whenUnhandled {
    case Event(PlayerDisconnected(player), data) =>
      stay using data.copy(playerRefs = data.playerRefs - player)
  }

  initialize()
}
