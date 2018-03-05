package io.paradoxical.ecsv

import io.paradoxical.ecsv.tasks.server.ServerTask
import io.paradoxical.tasks.{Task, TaskEnabledApp}

object Ecsv extends TaskEnabledApp {
  override def appName: String = "ecsv"

  lazy val serverTasks = new ServerTask

  override def defaultTask: Option[Task] = Some(serverTasks)

  override def tasks: List[Task] = List(serverTasks)
}
