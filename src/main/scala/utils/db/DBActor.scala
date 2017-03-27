package utils.db

import java.sql.{Connection, DriverManager}
import akka.actor.Actor
import com.typesafe.config.ConfigFactory

/**
  * = Actor-based interface between cluster actors & database =
  *{{{
  * Current database scheme:
  *
  * tasks:
  * +-----+--------+-------------+---------+
  * | id  | method |   status    | result  |
  * +-----+--------+-------------+---------+
  * | int | string | NOT_STARTED | SUCCESS |
  * |     |        | IN_PROCESS  | FAILURE |
  * |     |        | DONE        | ERROR   |
  * +-----+--------+-------------+---------+
  * }}}
  * All messages that are meant to be sent to this actor use [[utils.db.DBMessage]].
  */
class DBActor extends Actor {

  /**
    * = Connects to the configured database =
    * Configuration is found @ ''application.conf'' @ section ''db''
    * @return [[java.sql.Connection]] Object or [[scala.None]]
    */
  def connect: Option[Connection] = try {
    val config = ConfigFactory.load()
    Class.forName(config.getString("db.driver"))
    Some(DriverManager.getConnection(
      config.getString("db.url"),
      config.getString("db.username"),
      config.getString("db.password")))
  }
  catch {
    case e: Exception =>
      println(Console.RED + e.getMessage)
      None
  }

  /**
    * = Creates task entry in the database =
    * @param method name of the task to be saved; __must be unique__
    */
  def createTask(method : String): Unit = {
    connect match {
      case Some(connection) =>
        val statement = connection.createStatement()
        val sql = s"INSERT INTO tasks (method) VALUES ('$method');"
        try {
          // auto commit is on
          val result = statement.executeUpdate(sql)
          assert(result equals 1)
          println(Console.GREEN + "task added")
        }
        catch {
          case e: Exception =>
            println(Console.RED + e.getMessage)
        }
        finally connection.close()
      case None =>
        println(Console.RED + "could not connect")
    }
  }

  /**
    * = Creates several task entries in the database =
    * @param methods list w/ names of tasks to be saved; __names must be unique__
    */
  def createTasks(methods : List[String]): Unit = {
    connect match {
      case Some(connection) =>
        val statement = connection.createStatement()
        var sql = s"INSERT INTO tasks (method) VALUES"
        for (method <- methods) {
          sql += s" ('$method'),"
        }
        sql = sql.dropRight(1) + ";"
        try {
          // auto commit is on
          val result = statement.executeUpdate(sql)
          assert(result equals methods.size)
          println(Console.GREEN + s"${methods.size} tasks added")
        }
        catch {
          case e: Exception =>
            println(Console.RED + e.getMessage)
        }
        finally connection.close()
      case None =>
        println(Console.RED + "could not connect")
    }
  }

  /**
    * = Answers w/ requested task =
    * Some([[utils.db.RequestedTask]]) or [[scala.None]]
    * @param method name of requested task
    */
  def getTask(method : String): Unit = {
    connect match {
      case Some(connection) =>
        val statement = connection.createStatement()
        val sql = s"SELECT status, result FROM tasks WHERE method = '$method';"
        try {
          val resultSet = statement.executeQuery(sql)
          if (!resultSet.isBeforeFirst) {
            sender() ! None
            println(Console.RED + s"requested task: $method not found")
          }
          else while (resultSet.next()) {
            val status = TaskStatus.valueOf(resultSet.getString("status"))
            val result = resultSet.getString("result")
            sender() ! Some(RequestedTask(method, status, result))
            println(Console.GREEN + s"responsed w/ requested task:\n$method - $status - $result")
          }
        }
        catch {
          case e: Exception =>
            println(Console.RED + e.getMessage)
        }
        finally connection.close()
      case None =>
        println(Console.RED + "could not connect")
    }
  }

  /**
    * = Answers w/ requested tasks =
    * Some(List[ [[utils.db.RequestedTask]] ]) or [[scala.None]]
    * @param methods list w/ names of requested tasks
    */
  def getTasks(methods : List[String]): Unit = {
    connect match {
      case Some(connection) =>
        val statement = connection.createStatement()
        var sql = s"SELECT method, status, result FROM tasks WHERE method IN ("
        for (method <- methods) {
          sql += s"'$method', "
        }
        sql = sql.dropRight(2) + ");"
        try {
          val resultSet = statement.executeQuery(sql)
          if (!resultSet.isBeforeFirst) {
            sender() ! None
            println(Console.RED + s"requested tasks not found")
          }
          else {
            var taskList = List[RequestedTask]()
            while (resultSet.next()) {
              val method = resultSet.getString("method")
              val status = TaskStatus.valueOf(resultSet.getString("status"))
              val result = resultSet.getString("result")
              taskList = RequestedTask(method, status, result) :: taskList
              println(Console.GREEN + s"responsed w/ requested task:\n$method - $status - $result")
            }
            sender() ! Some(taskList)
          }
        }
        catch {
          case e: Exception =>
            println(Console.RED + e.getMessage)
        }
        finally connection.close()
      case None =>
        println(Console.RED + "could not connect")
    }
  }

  /**
    * = Answers w/ requested tasks =
    * Some(List[ [[utils.db.RequestedTask]] ]) or [[scala.None]]
    * @param status requested status from [[utils.db.TaskStatus]]
    */
  def getTasksWithStatus(status: TaskStatus): Unit = {
    connect match {
      case Some(connection) =>
        val statement = connection.createStatement()
        val sql = s"SELECT method, result FROM tasks WHERE status = '$status';"
        try {
          val resultSet = statement.executeQuery(sql)
          if (!resultSet.isBeforeFirst) {
            sender() ! None
            println(Console.RED + s"no tasks w/ status $status found")
          }
          else {
            var taskList = List[RequestedTask]()
            while (resultSet.next()) {
              val method = resultSet.getString("method")
              val result = resultSet.getString("result")
              taskList = RequestedTask(method, status, result) :: taskList
              println(Console.GREEN + s"responsed w/ requested task:\n$method - $status - $result")
            }
            sender() ! Some(taskList)
          }
        }
        catch {
          case e: Exception =>
            println(Console.RED + e.getMessage)
        }
        finally connection.close()
      case None =>
        println(Console.RED + "could not connect")
    }
  }

  /**
    * = Updates task w/ new status =
    * @param method name of requested task
    * @param status new status from [[utils.db.TaskStatus]]
    */
  def updateTaskStatus(method : String, status : TaskStatus): Unit = {
    connect match {
      case Some(connection) =>
        val statement = connection.createStatement()
        val sql = s"UPDATE tasks SET status = '$status' WHERE method = '$method';"
        try {
          // auto commit is on
          val result = statement.executeUpdate(sql)
          assert(result equals 1)
          println(Console.GREEN + "task updated")
        }
        catch {
          case e: Exception =>
            println(Console.RED + e.getMessage)
        }
        finally connection.close()
      case None =>
        println(Console.RED + "could not connect")
    }
  }

  /**
    * = Updates several tasks w/ new status =
    * @param methods list w/ names of requested tasks
    * @param status new status for all tasks from [[utils.db.TaskStatus]]
    */
  def updateTasksStatus(methods : List[String], status : TaskStatus): Unit = {
    connect match {
      case Some(connection) =>
        val statement = connection.createStatement()
        var sql = s"UPDATE tasks SET status = '$status' WHERE method IN ("
        for (method <- methods) {
          sql += s"'$method', "
        }
        sql = sql.dropRight(2) + ");"
        try {
          // auto commit is on
          val result = statement.executeUpdate(sql)
          assert(result equals methods.size)
          println(Console.GREEN + s"${methods.size} tasks updated")
        }
        catch {
          case e: Exception =>
            println(Console.RED + e.getMessage)
        }
        finally connection.close()
      case None =>
        println(Console.RED + "could not connect")
    }
  }

  /**
    * = Deletes requested task =
    * @param method name of requested task
    */
  def deleteTask(method : String): Unit = {
    connect match {
      case Some(connection) =>
        val statement = connection.createStatement()
        val sql = s"DELETE FROM tasks WHERE method = '$method';"
        try {
          // auto commit is on
          val result = statement.executeUpdate(sql)
          assert(result equals 1)
          println(Console.GREEN + "task deleted")
        }
        catch {
          case e: Exception =>
            println(Console.RED + e.getMessage)
        }
        finally connection.close()
      case None =>
        println(Console.RED + "could not connect")
    }
  }

  /**
    * = Deletes requested tasks =
    * @param methods list w/ names of requested tasks
    */
  def deleteTasks(methods : List[String]): Unit = {
    connect match {
      case Some(connection) =>
        val statement = connection.createStatement()
        var sql = s"DELETE FROM tasks WHERE method IN ("
        for (method <- methods) {
          sql += s"'$method', "
        }
        sql = sql.dropRight(2) + ");"
        try {
          // auto commit is on
          val result = statement.executeUpdate(sql)
          assert(result equals methods.size)
          println(Console.GREEN + s"${methods.size} tasks deleted")
        }
        catch {
          case e: Exception =>
            println(Console.RED + e.getMessage)
        }
        finally connection.close()
      case None =>
        println(Console.RED + "could not connect")
    }
  }

  override def receive: Receive = {
    case CreateTask(method) =>
      createTask(method)
    case CreateTasks(methods) =>
      createTasks(methods)
    case GetTask(method) =>
      getTask(method)
    case GetTasks(methods) =>
      getTasks(methods)
    case GetTasksWithStatus(status) =>
      getTasksWithStatus(status)
    case UpdateTaskStatus(method, status) =>
      updateTaskStatus(method, status)
    case UpdateTasksStatus(methods, status) =>
      updateTasksStatus(methods, status)
    case DeleteTask(method) =>
      deleteTask(method)
    case DeleteTasks(methods) =>
      deleteTasks(methods)
  }
}
