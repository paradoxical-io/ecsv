package io.paradoxical.ecsv.tasks.server

import io.paradoxical.ecsv.tasks.server.modules.Modules
import io.paradoxical.tasks.{Task, TaskDefinition}
import scala.concurrent.ExecutionContext.Implicits.global

class ServerTask extends Task {
  override type Config = Unit

  override def emptyConfig: Unit = Unit

  override def definition: TaskDefinition[Unit] = new TaskDefinition[Unit](
    name = "server",
    description = "Runs the ECS viewer server"
  )

  override def execute(args: Unit): Unit = {
    new Server(Modules()).main(Array.empty)
  }
}
