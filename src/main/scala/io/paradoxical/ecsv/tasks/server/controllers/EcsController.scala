package io.paradoxical.ecsv.tasks.server.controllers

import com.twitter.finatra.request.{QueryParam, RouteParam}
import io.paradoxical.ecsv.aws.ecs.{EcsProvider, EcsService}
import io.paradoxical.finatra.Framework
import io.paradoxical.finatra.swagger.annotations.ApiData
import java.net.URLDecoder
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class EcsController @Inject()(ecsProvider: EcsProvider)(implicit executionContext: ExecutionContext) extends Framework.RestApi {
  getWithDoc("/api/v1/ecs/clusters/:clusterName/services") {
    _.tag("ECS").
      description("Gets ecs services").
      request[ListServicesRequest].
      responseWith[List[ServiceDto]](status = 200)
  } { request: ListServicesRequest =>
    for {
      services <- ecsProvider.getServices(request.clusterName)
    } yield {
      val filtered = request.search.map(search => services.filter(_.getServiceName.contains(search))).getOrElse(services)

      filtered.map(s => ServiceDto(name = s.getServiceName, arn = s.getServiceArn, status = s.getStatus))
    }
  }

  getWithDoc("/api/v1/ecs/services/:arn") {
    _.tag("ECS").
      description("Gets  service detail data").
      request[GetByArnRequest].
      responseWith[EcsService](status = 200)
  } { request: GetByArnRequest =>
    ecsProvider.getDetailService(URLDecoder.decode(request.arn, "UTF-8"))
  }
}

case class GetByArnRequest (
  @RouteParam arn: String
)

case class ListServicesRequest (
  @RouteParam clusterName: String,

  @ApiData(description = "Optional search filter")
  @QueryParam search: Option[String]
)

case class ServiceDto(
  name: String,
  arn: String,
  status: String
)