package part2_event_sourcing

import java.util.Date

import akka.actor.{ActorLogging, ActorSystem, Props}
import akka.persistence.PersistentActor

object PersistentActors extends App{

  /*
    Scenario: we have a business and an accountant which keeps track of our business.
   */
  //COMMANDS
  case class Invoice(recipient: String, date: Date, amount: Int)
  case class InvoiceBulk(invoices: List[Invoice])

  //Special messages
  case object Shutdown
  //EVENTS
  case class InvoiceRecorded(id: Int, recipient: String, date: Date, amount: Int)

  class Accountant extends PersistentActor with ActorLogging {

    var latestInvoiceId = 0
    var totalAmount = 0

    override def persistenceId: String = "simple-accountant"

    /**
      * the "normal" receive method
      */
    override def receiveCommand: Receive = {
      case Invoice(recipient,date,amount) => {
        /*
          When you receive a command
            1) create an EVENT to persist into the store
            2) persist the EVENT, then pass in a callback that will get triggered once the event is written
            3) update the actor state when the event has persisted
         */
        log.info(s"Receive the invoice for amount: $amount")
        val event = InvoiceRecorded(latestInvoiceId,recipient,date,amount)
        persist(event)
        /*Time gap: all messages sent to this actor are stashed*/
        { e =>
            //SAFE to access mutable state here
            //update state
            latestInvoiceId += 1
            totalAmount += amount

            // correctly identify sender of the command
            sender() ! "PersistenceACK"
            log.info(s"Persisted $e as invoice #${e.id} for total amount $totalAmount")
        }
      }
      case InvoiceBulk(invoices) => {
        /*
          create events
          persist all the events
          update the actor state when each event is persisted
         */
        val invoiceIds = latestInvoiceId to (latestInvoiceId + invoices.size)
        val events = invoices.zip(invoiceIds).map{pair =>
          val id = pair._2
          val invoice = pair._1

          InvoiceRecorded(id,invoice.recipient,invoice.date,invoice.amount)
        }
        persistAll(events) {e =>
          latestInvoiceId+=1
          totalAmount+= e.amount
          log.info(s"Persisted SINGLE $e as invoice #${e.id} for total amount $totalAmount")
        }
      }
      case "print" =>
        log.info(s"latest invoice id: $latestInvoiceId, total amount: $totalAmount")

      case Shutdown => context.stop(self)
    }

    /**
      * Handler that will be called on recovery
    */
    override def receiveRecover: Receive = {
      /*
        best practice: follow the logic in the persist steps of receiveCommand
       */
      case InvoiceRecorded(id, _, _, amount) => {
        log.info(s"Recovered invoice #$id for amount $amount, total amount: $totalAmount")
        latestInvoiceId = id
        totalAmount += amount
      }
    }
    /*
      This method will be called if persisting failed.
      The actor will be STOPPED.
      Best practice: start the actor again after a while
     */
    override def onPersistFailure(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Fail to persist $event because of $cause")
      super.onPersistFailure(cause,event,seqNr)
    }

    /*
      Called if the JOURNAL fails to persist the event
      The actor is RESUMED.
     */
    override def onPersistRejected(cause: Throwable, event: Any, seqNr: Long): Unit = {
      log.error(s"Persist rejected for $event because of $cause")
      super.onPersistRejected(cause, event, seqNr)
    }
  }

  val system = ActorSystem("PersistentActor")
  val accountant = system.actorOf(Props[Accountant],"simpleAccountant")


  /**
    * Persisting multiple events
    */
  val newInvoices = for(i <- 1 to 10) yield Invoice("The Sofa Company", new Date, i*1000)
  //  accountant ! InvoiceBulk(newInvoices.toList)

  /*
    NEVER EVER CALL PERSIST OR PERSISTALL FROM FUTURES.
   */
  /**
    * Shutdown of persistent actors
    *
    * Best practice: define your own shutdown message.
    */

}
