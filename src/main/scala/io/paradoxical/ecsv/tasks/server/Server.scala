package io.paradoxical.ecsv.tasks.server

import com.google.inject.Module
import com.twitter.finagle.http.{Request, Response}
import com.twitter.finatra.http.filters.{CommonFilters, LoggingMDCFilter, TraceIdMDCFilter}
import com.twitter.finatra.http.routing.HttpRouter
import io.paradoxical.ecsv.tasks.server.controllers.{EcsController, UiController}
import io.paradoxical.finatra.HttpServiceBase
import io.paradoxical.finatra.swagger.ApiDocumentationConfig

class Server(override val modules: List[Module]) extends HttpServiceBase {
  override def defaultFinatraHttpPort = ":9999"

  override def documentation = new ApiDocumentationConfig {
    override val description: String = "ECS Viewer"
    override val title: String = "API"
    override val version: String = "1.0"
  }

  override def configureHttp(router: HttpRouter): Unit = {
    router
      .filter[LoggingMDCFilter[Request, Response]]
      .filter[TraceIdMDCFilter[Request, Response]]
      .filter[CommonFilters]
      .add[EcsController]
      .add[UiController]

    configureDocumentation(router)
  }
}