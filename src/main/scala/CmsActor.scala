import akka.actor.{ActorRef, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import com.clearspring.analytics.stream.frequency.CountMinSketch

import scala.concurrent.duration._

object CmsActor {
  def props(msgType: String) = Props(classOf[CmsActor], msgType)
}

class CmsActor(msgType: String) extends PersistentActor {
  val epsOfTotalCount: Double = 0.0001
  val confidence: Double = 0.99
  var cms: CountMinSketch = new CountMinSketch(epsOfTotalCount, confidence, 1)
  var total = 0
  var reportRecoveryStatusTo: ActorRef = null
  var recoveryCompleted = false

  implicit val ec = context.dispatcher
  context.system.scheduler.schedule(0.seconds, 10.seconds, self, "snap")

  override def receiveRecover: Receive = {

    case event: TYPED_DATA_EVENT =>
      updateState(event)
    case SnapshotOffer(_, snapshot: CountMinSketch) =>
      cms = snapshot
    case RecoveryCompleted =>
      recoveryCompleted = true
      if (reportRecoveryStatusTo != null)
        reportRecoveryStatusTo ! true
  }

  override def receiveCommand: Receive = {
    case WhenAskedRecoveryCompleted =>
      if (!recoveryCompleted)
        reportRecoveryStatusTo = sender()
      else
        sender() ! true
    case "snap" =>
      saveSnapshot(cms)
    case e:TYPED_DATA_EVENT =>
      val _sender = sender()
      persist(e) { event =>
        total += 1
        updateState(e)
        _sender ! "ACK"
      }
  }

  private def updateState(e: TYPED_DATA_EVENT): Unit = {
    cms.add(e.key, e.freq)
  }

  override def persistenceId: String = msgType
}