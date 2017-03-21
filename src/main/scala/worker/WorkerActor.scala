package worker
import akka.actor.{Actor, Props}
import worker.messages.AddTask

object WorkerActor extends Actor{

  print(self.path.name + " was created!")

  override def receive: Receive = {
    case p : AddTask => addTask(p)
  }

  def addTask(msg : AddTask) = {
    val api = msg.group.head
    context.child(api) match {
      case Some(child) => child ! msg
      case None => {
        msg.task.singleInstance match {
          case false => context.actorOf(Props(classOf[GroupActor], msg.group.take(1)), api) ! msg
          case true => context.actorOf(Props(classOf[SingleInstanceActor], msg.group.take(1)), api) ! msg
        }
      }
    }
  }
}
