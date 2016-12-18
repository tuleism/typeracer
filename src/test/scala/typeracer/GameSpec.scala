package typeracer

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.{ImplicitSender, TestActorRef, TestKit, TestProbe}
import org.scalatest.{MustMatchers, WordSpecLike}
import spray.json._

import scala.concurrent.duration._

class TestGameMaster extends GameMaster {
  override def getQuote(): String = "This is a very simple quote"
}

class GameSpec extends TestKit(ActorSystem("testGame"))
  with GameEventMarshalling
  with ImplicitSender
  with WordSpecLike
  with MustMatchers
  with StopSystemAfterAll {

  val player1 = Player("1", "tule")
  val player2 = Player("2", "sumo")

  "a GameMaster" must {
    val gameMaster = TestActorRef[TestGameMaster]
    val p1Actor = TestProbe()
    val p2Actor = TestProbe()
    val roomActor = TestProbe()
    val anotherRoomActor = TestProbe()

    "assign new player to a room" in {
      gameMaster.underlyingActor.rooms = Map(roomActor.ref -> 0)
      gameMaster ! RoomRequest(player1, p1Actor.ref)

      expectMsg(AssignedRoom(roomActor.ref))
      assert(
        gameMaster.underlyingActor.rooms(roomActor.ref) == 1
      )
      roomActor.expectMsg(PlayerConnected(player1, p1Actor.ref))
    }

    "assign new player to a new room if all rooms are full" in {
      gameMaster.underlyingActor.rooms = Map(
        roomActor.ref -> gameMaster.underlyingActor.maxPlayersPerRoom,
        anotherRoomActor.ref -> 0
      )
      gameMaster ! RoomRequest(player2, p2Actor.ref)

      expectMsg(AssignedRoom(anotherRoomActor.ref))
      assert(
        gameMaster.underlyingActor.rooms(anotherRoomActor.ref) == 1
      )
      anotherRoomActor.expectMsg(PlayerConnected(player2, p2Actor.ref))
      roomActor.expectNoMsg(100.milliseconds)
    }

    "remove expired rooms" in {
      gameMaster ! RoomExpired(roomActor.ref)
      assert(
        gameMaster.underlyingActor.rooms == Map(anotherRoomActor.ref -> 1)
      )
    }
  }

  "The GameFlow" must {
    implicit val materializer = ActorMaterializer()
    val p1Actor = TestProbe()
    val flow: Flow[Message, Message, _] = new GameFlow {}.flow(p1Actor.ref, player1)

    "send player input to the represented actor" in {
      val source = TestSource.probe[Message]
        .via(flow)
        .toMat(Sink.ignore)(Keep.left)
        .run()

      p1Actor.expectMsgClass(classOf[PlayerConnected])
      source.sendNext(TextMessage("\"newGame\""))
      p1Actor.expectMsg(PlayerAction(NewGame))
      source.sendNext(TextMessage("1"))
      p1Actor.expectMsg(PlayerAction(AtPosition(player1, 1)))
      source.sendNext(TextMessage("2"))
      p1Actor.expectMsg(PlayerAction(AtPosition(player1, 2)))
    }

    "send out correct game output" in {
      val sink = Source(Nil)
        .via(flow)
        .toMat(TestSink.probe[Message])(Keep.right)
        .run()
      val problemStatement = ProblemStatement("This is a simple quote", 5)
      val p1State = (player1, PlayerState(0, None, 0))
      val p2State = (player2, PlayerState(5, Some(1), 20))
      val stateUpdate = StateUpdate(Map(p1State, p2State))
      val stateUpdateJson = JsObject(
        "current" -> p1State.toJson,
        "others" -> JsArray(p2State.toJson)
      )

      val PlayerConnected(_, outputRef) = p1Actor.expectMsgClass(classOf[PlayerConnected])
      outputRef ! problemStatement
      sink.request(1)
      sink.expectNext(TextMessage(problemStatement.toJson.toString))

      outputRef ! stateUpdate
      sink.request(1)
      sink.expectNext(TextMessage(stateUpdateJson.toString))
    }
  }
}
