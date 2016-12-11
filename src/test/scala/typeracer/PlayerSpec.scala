package typeracer

import akka.actor.ActorSystem
import akka.testkit.{TestActorRef, TestFSMRef, TestKit, TestProbe}
import org.scalatest.{MustMatchers, WordSpecLike}

class PlayerSpec extends TestKit(ActorSystem("testPlayer"))
  with WordSpecLike
  with MustMatchers
  with StopSystemAfterAll {

  val player = Player("1", "tule")

  "a PlayerRepr" must {
    val gmProbe = TestProbe()
    val fsm = TestFSMRef(new PlayerRepr(gmProbe.ref))
    val playerRepr: TestActorRef[PlayerRepr] = fsm
    val playerActor = TestProbe()
    val roomActor = TestProbe()
    val connectedMsg = PlayerConnected(player, playerActor.ref)

    "wait for player to join" in {
      assert(fsm.stateName == ReprWaitingForPlayer)
      playerRepr ! connectedMsg
      assert(fsm.stateName == ReprWaitingForRoom)
      assert(fsm.stateData == PlayerReprData(Some(connectedMsg), None))
    }

    "change state to playing when being assigned a room" in {
      playerRepr ! AssignedRoom(roomActor.ref)
      assert(fsm.stateName == ReprPlaying)
      assert(fsm.stateData == PlayerReprData(Some(connectedMsg), Some(roomActor.ref)))
    }

    "forward playing input to the assigned room" in {
      val input = PlayerAction(AtPosition(player, 0))
      playerRepr ! input
      roomActor.expectMsg(AtPosition(player, 0))
    }

    "attempt to find a room when player sends NewGame" in {
      playerRepr ! PlayerAction(NewGame)
      assert(fsm.stateName == ReprWaitingForRoom)
      gmProbe.expectMsg(RoomRequest(player, playerActor.ref))
    }

    "forward disconnected signal to assigned room and then stop" in {
      val input = PlayerDisconnected(player)
      val watchProbe = TestProbe()
      watchProbe.watch(playerRepr)

      playerRepr ! input
      roomActor.expectMsg(input)
      watchProbe.expectTerminated(playerRepr)
    }

  }
}
