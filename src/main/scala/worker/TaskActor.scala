package worker

import Exceptions._
import akka.actor.SupervisorStrategy.{Escalate, Stop}
import akka.actor.{ActorRef, OneForOneStrategy, SupervisorStrategy, Terminated}
import akka.cluster.Cluster
import akka.pattern._
import akka.util.Timeout
import worker.messages._
import scala.concurrent.duration._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

/**
  * Created by mischcon on 21.03.17.
  */
class TaskActor(task : Task) extends WorkerTrait{

  var isTaken : Boolean = false
  var taskDone : Boolean = false

  implicit val timeout = Timeout(2 seconds)
  implicit val ec : ExecutionContext = ExecutionContext.Implicits.global

  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    log.debug(s"Hello from ${self.path.name}")
  }

  override def postStop(): Unit = {
    log.debug(s"Goodbye from ${self.path.name}")
  }

  /*
  * POSITIVE PATH: if the task has been done
  *
  * Success: Stop the ExecutorActor
  * Failure: Inform the parent actor --> he should stop all other child actors
  *
  * */
  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(){
    case TestSuccessException => {
      taskDone = true
      Stop
    }
    case TestFailException => {
      taskDone = true
      Escalate
    }
  }

  /*
  * Terminated: This event can occur because of two different causes
  * 1) The executing actor has been shutted down because of the STOP directive
  *    from the supervisorStrategy above (implies that taskDone = true)
  *    In this case simply shut down the TaskActor because it has done its work
  * 2) The executing actor is UNREACHABLE because the node has been disconnected
  *    In this case taskDone = false, which means that the task still needs to be
  *    executed (re-run) - so simply set isTaken back to false and wait until
  *    another Executor asks for a new task
  * */
  override def receive: Receive = {
    case GetTask if ! isTaken => handleGetTask()
    case Terminated if taskDone => context.stop(self)
    case Terminated if ! taskDone => isTaken = false
  }

  def handleGetTask() = {
    isTaken = true
    val executor = sender() ? SendTask(task)
    executor.onComplete{
      case Success(actorRef : ActorRef) => {
        context.watch(actorRef)
      }
      case Failure(Object) => {
        isTaken = false
      }
    }
  }
}
