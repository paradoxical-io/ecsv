package io.paradoxical.ecsv.tasks.server.controllers

import com.twitter.finagle.http.Request
import com.twitter.finatra.request.{QueryParam, RouteParam}
import io.paradoxical.ecsv.aws.ecs._
import io.paradoxical.finatra.Framework
import io.paradoxical.finatra.swagger.annotations.ApiData
import java.net.URLDecoder
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class EcsController @Inject()(ecs: EcsProvider)(implicit executionContext: ExecutionContext) extends Framework.RestApi {
  getWithDoc("/api/v1/ecs/clusters/:clusterName/services") {
    _.tag("ECS").
      description("Gets ecs services").
      request[ListServicesRequest].
      responseWith[List[ServiceDto]](status = 200)
  } { request: ListServicesRequest =>
    for {
      services <- ecs.listServices(request.clusterName)
    } yield {
      val filtered = request.search.map(search => services.filter(_.getServiceName.contains(search))).getOrElse(services)

      filtered.map(s => ServiceDto(name = s.getServiceName, arn = s.getServiceArn, status = s.getStatus))
    }
  }

  getWithDoc("/api/v1/ecs/clusters") {
    _.tag("ECS").
      description("Lists clusters").
      responseWith[List[EcsCluster]](status = 200)
  } { _: Request =>
    ecs.getClusters
  }

  getWithDoc("/api/v1/ecs/clusters/:arn") {
    _.tag("ECS").
      description("Gets clusters details").
      request[GetByArnRequest].
      responseWith[EcsClusterDetail](status = 200)
  } { request: GetByArnRequest =>
    ecs.getClusterDetails(URLDecoder.decode(request.arn, "UTF-8"))
  }

  getWithDoc("/api/v1/ecs/services/:arn") {
    _.tag("ECS").
      description("Gets service detail data").
      request[GetByArnRequest].
      responseWith[EcsService](status = 200)
  } { request: GetByArnRequest =>
    ecs.getServiceDetails(URLDecoder.decode(request.arn, "UTF-8"))
  }

  deleteWithDoc("/api/v1/ecs/cache") {
    _.tag("Cache").
      description("Flushes internal caches")
  } { _: Request =>
    ecs.invalidateCache()
  }
}

case class GetByArnRequest(
  @RouteParam arn: String
)

case class ListServicesRequest(
  @RouteParam clusterName: String,

  @ApiData(description = "Optional search filter")
  @QueryParam search: Option[String]
)

case class ServiceDto(
  name: String,
  arn: String,
  status: String
)