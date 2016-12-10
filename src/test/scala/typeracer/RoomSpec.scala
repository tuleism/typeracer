package typeracer

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestFSMRef, TestKit, TestProbe}
import org.scalatest.{MustMatchers, WordSpecLike}

import scala.concurrent.duration._

class RoomSpec extends TestKit(ActorSystem("testRoom"))
  with WordSpecLike
  with MustMatchers
  with StopSystemAfterAll {

  val quote = "this is a racing competition"
  val player1 = Player("1", "tule")
  val player2 = Player("2", "sumo")
  val player3 = Player("3", "quynhanh")

  "A Room" must {
    val fsm = TestFSMRef(new Room(quote))
    val room: TestActorRef[Room] = fsm
    val p1Actor = TestProbe()
    val p2Actor = TestProbe()
    val p3Actor = TestProbe()

    "wait for more than one player to start" in {
      val player1Join = StateUpdate(
        Map(player1 -> PlayerState(0, None, 0))
      )
      room ! PlayerConnected(player1, p1Actor.ref)
      p1Actor.expectMsg(player1Join)
      p1Actor.expectNoMsg(100.milliseconds)
    }

    "broadcast the 2nd player and start the timer" in {
      val player2Join = StateUpdate(
        Map(player1 -> PlayerState(0, None, 0), player2 -> PlayerState(0, None, 0))
      )
      room ! PlayerConnected(player2, p2Actor.ref)
      p1Actor.expectMsg(player2Join)
      p2Actor.expectMsg(player2Join)
      within(1.second) {
        p1Actor.expectMsg(ProblemStatement(quote, 7))
        p2Actor.expectMsg(ProblemStatement(quote, 7))
      }
    }

    "broadcast the 3rd player and send the correct start time" in {
      val player3Join = StateUpdate(
        Map(
          player1 -> PlayerState(0, None, 0),
          player2 -> PlayerState(0, None, 0),
          player3 -> PlayerState(0, None, 0)
        )
      )
      room ! PlayerConnected(player3, p3Actor.ref)
      p1Actor.expectMsg(player3Join)
      p2Actor.expectMsg(player3Join)
      p3Actor.expectMsg(player3Join)
      p3Actor.expectMsg(1.second, ProblemStatement(quote, 6))
      p1Actor.expectNoMsg(100.milliseconds)
      p2Actor.expectNoMsg(100.milliseconds)
    }

    "update state correctly while racing" in {
      val initialState =
        Map(player1 -> PlayerState(0, None, 0), player2 -> PlayerState(0, None, 0))
      val player1Advance = StateUpdate(
        Map(player1 -> PlayerState(2, None, 1200), player2 -> PlayerState(0, None, 0))
      )
      val player2Finish = StateUpdate(
        Map(player1 -> PlayerState(2, None, 1200), player2 -> PlayerState(5, Some(1), 1500))
      )
      val player2Disconnect = StateUpdate(
        Map(player1 -> PlayerState(4, None, 1200), player2 -> PlayerState(5, Some(1), 1500))
      )
      val playerRefs = Map(player1 -> p1Actor.ref, player2 -> p2Actor.ref)
      val currentTime = System.currentTimeMillis()

      fsm.setState(
        RacingInProgress,
        RoomData(Some(0), Some(currentTime), Set.empty, playerRefs, initialState)
      )

      // player1 send his position
      Thread.sleep(100)
      room ! AtPosition(player1, 2)
      p1Actor.expectMsg(player1Advance)
      p2Actor.expectMsg(player1Advance)

      // player2 finish, player1 state stays the same
      Thread.sleep(100)
      room ! AtPosition(player2, 5)
      p1Actor.expectMsg(player2Finish)
      p2Actor.expectMsg(player2Finish)

      room ! PlayerDisconnected(player2)
      room ! AtPosition(player1, 4)
      p1Actor.expectMsg(player2Disconnect)
      p2Actor.expectNoMsg(100.milliseconds)
    }
  }
}
