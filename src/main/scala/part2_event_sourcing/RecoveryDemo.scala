package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.{PersistentActor, Recovery, RecoveryCompleted, SnapshotSelectionCriteria}

object RecoveryDemo extends  App{

  case class Command(contents: String)
  case class Event(id: Int,contents: String)
  class RecoveryActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "recovery-Actor"

    override def receiveCommand: Receive = online(0)
//    override def receiveCommand: Receive = {
//      case Command(contents) => {
//        persist(Event(latestPersistedEventId,contents)) {event =>
//          log.info(s"Successfull persisted $event, recovery is ${if(this.recoveryFinished) "" else  "NOT"} finished.")}
//        latestPersistedEventId += 1
//      }
//    }

    def online(latestPersistedEventId: Int): Receive = {
      case Command(contents) => {
        persist(Event(latestPersistedEventId, contents)) { event =>
          log.info(s"Successfull persisted $event, recovery is ${if (this.recoveryFinished) "" else "NOT"} finished.")
        }
        context.become(online(latestPersistedEventId + 1))
      }
    }

    override def receiveRecover: Receive = {
      case RecoveryCompleted =>
        {
          // additional initialization
          log.info("I have finished recovery")
        }
      case Event(id,contents) => {
//        if(contents.contains("314"))
//          throw new RuntimeException("I can't take this anymore")
        log.info(s"Recovered $contents, recovery is ${if(this.recoveryFinished) "" else  "NOT"} finished.")
        context.become(online(id))
        /*
          This will not change the event handler during recovery
          AFTER recovery the "normal" handler will be the result of all the stacking of context.become
         */
      }
    }

    override def onRecoveryFailure(cause: Throwable, event: Option[Any]): Unit = {
      log.error("I failed at recovery")
      super.onRecoveryFailure(cause, event)
    }

    override def recovery: Recovery = Recovery(fromSnapshot = SnapshotSelectionCriteria.Latest)
//    override def recovery: Recovery = Recovery.none
  }

  val system = ActorSystem("RecoveryDemo")

  val recoveryActor = system.actorOf(Props[RecoveryActor],"RecoveryActor")

  /*
  Stashing commands
   */
//  for (i <- 1 to 1000){
//    recoveryActor ! Command(s"command $i")
//  }
  //ALL COMMANDS SENT DURING RECOVERY ARE STASHED

  /*
    2 - failure during recovery
      - onRecoveryFailure + the actor is STOPPED.
   */
  /*
    3 - customizing recovery
      - DO NOT persist more events after a incomplete recovery
   */
  /*
    4 - recovery status or KNOWING when you're done recovering
      - getting a signal when you're done with recovery
   */
  /*
    5 - stateless actors
   */
  recoveryActor ! Command("Special Command 1")
  recoveryActor ! Command("Special Command 2")
}
