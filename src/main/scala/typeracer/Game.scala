package typeracer

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, Sink, Source}
import akka.stream.{FlowShape, OverflowStrategy}
import spray.json._

import scala.concurrent.duration._
import scala.util.Try

trait GameFlow extends GameEventMarshalling {
  def flow(playerRepr: ActorRef, player: Player): Flow[Message, Message, _] = {
    val gameOutputSource = Source.actorRef[GameOutputEvent](10, OverflowStrategy.fail)

    Flow.fromGraph(
      GraphDSL.create(gameOutputSource) { implicit builder =>
        gameOutput =>
          import GraphDSL.Implicits._

          val materialization = builder.materializedValue.map(PlayerConnected(player, _))

          val playerRawInput = builder.add(Flow[Message].collect {
            case TextMessage.Strict(txt) => txt
          }.map {
            case "\"newGame\"" => NewGame
            case pos => AtPosition(player, Try(pos.toInt).getOrElse(0))
          }.map(
            PlayerAction(_)
          ))

          val merge = builder.add(Merge[GameInputEvent](2))

          val playerReprSink = Sink.actorRef[GameInputEvent](playerRepr, PlayerDisconnected(player))

          val outputToPlayer = builder.add(Flow[GameOutputEvent].map {
            case msg: ProblemStatement => msg.toJson
            case StateUpdate(stateMap) => JsObject(
              "current" -> (player, stateMap(player)).toJson,
              "others" -> JsArray(stateMap.filterKeys(_ != player).map(_.toJson).toVector)
            )
          }.map {
            js => TextMessage(js.toString)
          }.keepAlive(
            30.seconds, () => TextMessage("keepalive")
          ))

          // behind the scene, playerRepr send message of type GameOutputEvent to gameOutput
          materialization ~> merge ~> playerReprSink
          playerRawInput ~> merge

          gameOutput ~> outputToPlayer

          FlowShape(playerRawInput.in, outputToPlayer.out)
      }
    )
  }
}

object GameMaster {
  def props: Props = Props[GameMaster]
}

class GameMaster extends Actor with ActorLogging with Quoting {
  val maxPlayersPerRoom = 7
  var rooms: Map[ActorRef, Int] = Map[ActorRef, Int]()

  def receive: Receive = {
    case RequestUserRepr(player) =>
      val reprRef = context.actorOf(PlayerRepr.props(self))
      sender() ! reprRef

    case RoomExpired(roomRef) =>
      rooms = rooms - roomRef

    case RoomRequest(player, ref) =>
      val openRooms = rooms.find(_._2 < maxPlayersPerRoom)
      val assignedRoom = openRooms.orElse {
        val newRoom = context.actorOf(Room.props(getQuote()))
        Some((newRoom, 0))
      }
      assignedRoom.foreach { kv =>
        val noPlayers = kv._2 + 1
        rooms = rooms + (kv._1 -> noPlayers)
        sender() ! AssignedRoom(kv._1)
        kv._1 ! PlayerConnected(player, ref)
      }
  }
}