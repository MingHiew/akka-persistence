package part2_event_sourcing

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

object RecoveryDemo extends  App{

  case class Command(contents: String)
  case class Event(cotents: String)
  class RecoveryActor extends PersistentActor with ActorLogging {
    override def persistenceId: String = "recovery-Actor"

    override def receiveCommand: Receive = {
      case Command(contents) => {
        persist(Event(contents)) {event =>
          log.info(s"Successfull persisted $event")
        }
      }
    }

    override def receiveRecover: Receive = {
      case Event(contents) => {
        log.info(s"Recovered $contents")
      }
    }
  }

  val system = ActorSystem("RecoveryDemo")

  val recoveryActor = system.actorOf(Props[RecoveryActor],"RecoveryActor")

  /*
  Stashing commands
   */
  for (i <- 1 to 1000){
    recoveryActor ! Command(s"command $i")
  }
  //ALL COMMANDS SENT DURING RECOVERY ARE STASHED
}
