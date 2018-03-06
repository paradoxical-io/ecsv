package io.paradoxical.ecsv.aws.ecs

import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.ec2.model._
import com.amazonaws.services.ecs.AmazonECSAsync
import com.amazonaws.services.ecs.model._
import com.google.common.cache.CacheBuilder
import com.google.inject.Inject
import io.paradoxical.ecsv.util.Futures.Implicits._
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object EcsProvider {
  private def hydrate[Req, Resp](
    nextRequest: (Option[String]) => Req,
    source: Req => java.util.concurrent.Future[Resp],
    tokenProvider: Resp => Option[String]
  )(implicit executionContext: ExecutionContext): Future[List[Resp]] = {
    def hydrate0(token: Option[String], acc: List[Option[Resp]] = Nil, root: Boolean = false): Future[List[Resp]] = {
      if (token.isEmpty && !root) {
        Future.successful(acc.flatten)
      } else {
        source(nextRequest(token)).toScalaFuture().flatMap(r => {
          hydrate0(tokenProvider(r), acc :+ Some(r))
        })
      }
    }

    hydrate0(None, Nil, root = true)
  }
}

@Singleton
class EcsProvider @Inject()(
  ecs: AmazonECSAsync,
  ec2: AmazonEC2Async
)(implicit executionContext: ExecutionContext) {

  class Caches {
    lazy val clusters = CacheBuilder.
      newBuilder().expireAfterWrite(3, TimeUnit.MINUTES).
      build[String, Future[Seq[EcsCluster]]]

    lazy val fullServiceCache = CacheBuilder.
      newBuilder().expireAfterWrite(15, TimeUnit.SECONDS).
      build[String, Future[Option[EcsService]]]

    lazy val slimServices = CacheBuilder.
      newBuilder().expireAfterWrite(3, TimeUnit.MINUTES).
      build[String, Future[Seq[Service]]]

    lazy val containersCache = CacheBuilder.
      newBuilder().expireAfterWrite(3, TimeUnit.MINUTES).
      build[String, Future[ContainerInstance]]

    lazy val hostCache = CacheBuilder.
      newBuilder().expireAfterWrite(3, TimeUnit.MINUTES).
      build[String, Future[Instance]]

    def invalidate() = {
      hostCache.invalidateAll()
      containersCache.invalidateAll()
      slimServices.invalidateAll()
      fullServiceCache.invalidateAll()
      clusters.invalidateAll()
    }
  }

  private lazy val cache = new Caches

  def invalidateCache() = cache.invalidate()

  def getClusters: Future[Seq[EcsCluster]] = {
    cache.clusters.get("cluster", () => listClustersUnCached())
  }

  def listServices(cluster: String): Future[Seq[Service]] = {
    cache.slimServices.get(cluster, () => {
      for {
        arns <- listServiceArns(cluster)
        services <- Future.sequence(arns.grouped(10).map(getServiceSimple))
      } yield {
        services.flatten.toSeq
      }
    })
  }

  def getServiceDetails(arn: String): Future[Option[EcsService]] = {
    cache.fullServiceCache.get(arn, () => getHydratedService(arn))
  }

  def getClusterDetails(arn: String): Future[EcsClusterDetail] = {
    for {
      instances <-
          EcsProvider.hydrate[ListContainerInstancesRequest, ListContainerInstancesResult] (
            token => new ListContainerInstancesRequest().withCluster(arn).withNextToken(token.orNull),
            ecs.listContainerInstancesAsync,
            r => Option(r.getNextToken)
          )

      instanceArns = instances.flatMap(_.getContainerInstanceArns.asScala)

      instancesInCluster <- Future.sequence(
        instanceArns.map(instanceArn =>
          ecs.describeContainerInstancesAsync(
            new DescribeContainerInstancesRequest().
              withContainerInstances(instanceArn)
          ).toScalaFuture()
        )
      )
    } yield {
      EcsClusterDetail(
        instances = instancesInCluster.flatMap(_.getContainerInstances.asScala).map(instance => {
          val count = ServiceCount(active = instance.getRunningTasksCount, pending = instance.getPendingTasksCount)

          ContainerInstanceData(instance.getEc2InstanceId, count)
        })
      )
    }
  }

  private def listClustersUnCached(): Future[List[EcsCluster]] = {
    for {
      clustersArns <- ecs.listClustersAsync().toScalaFuture()
      clusters <- ecs.describeClustersAsync(new DescribeClustersRequest().withClusters(clustersArns.getClusterArns)).toScalaFuture()
    } yield {
      clusters.getClusters.asScala.toList.map(c => EcsCluster(
        name = c.getClusterName,
        arn = c.getClusterArn,
        serviceCount = ServiceCount(c.getActiveServicesCount, c.getPendingTasksCount)
      ))
    }
  }

  private def listServiceArns(cluster: String): Future[Seq[String]] = {
    EcsProvider.hydrate[ListServicesRequest, ListServicesResult](
      token => new ListServicesRequest().withCluster(cluster).withNextToken(token.orNull),
      ecs.listServicesAsync,
      r => Option(r.getNextToken)
    ).map(_.flatMap(_.getServiceArns.asScala))
  }

  def getServiceSimple(services: Seq[String])(implicit executionContext: ExecutionContext): Future[Seq[Service]] = {
    for {
      services <- ecs.describeServicesAsync(new DescribeServicesRequest().withServices(services.asJava)).toScalaFuture
    } yield {
      services.getServices.asScala
    }
  }

  private def getHydratedService(service: String)(implicit executionContext: ExecutionContext): Future[Option[EcsService]] = {
    for {
      services <- ecs.describeServicesAsync(new DescribeServicesRequest().withServices(service)).toScalaFuture

      serviceOption = services.getServices.asScala.headOption

      service <-
        if (serviceOption.isEmpty) {
          Future.successful(None)
        } else {
          populateService(serviceOption.get).map(Some(_))
        }
    } yield {
      service
    }
  }

  private def getContainerInstance(arn: String): Future[ContainerInstance] = {
    ecs.describeContainerInstancesAsync(new DescribeContainerInstancesRequest().withContainerInstances(arn)).toScalaFuture().map(_.getContainerInstances.asScala.head)
  }

  private def populateService(service: Service)(implicit executionContext: ExecutionContext): Future[EcsService] = {
    for {
      definition <- ecs.describeTaskDefinitionAsync(new DescribeTaskDefinitionRequest().withTaskDefinition(service.getTaskDefinition)).toScalaFuture()
      taskArns <- ecs.listTasksAsync(new ListTasksRequest().withFamily(definition.getTaskDefinition.getFamily)).toScalaFuture()
      tasks <- if (taskArns.getTaskArns.asScala.nonEmpty) {
        ecs.describeTasksAsync(new DescribeTasksRequest().withTasks(taskArns.getTaskArns)).toScalaFuture()
      } else {
        Future.successful(new DescribeTasksResult())
      }

      containerArns = tasks.getTasks.asScala.map(_.getContainerInstanceArn)

      containers <- Future.sequence(containerArns.map(arn => cache.containersCache.get(arn, () => getContainerInstance(arn))))

      ec2InstanceIds = containers.map(_.getEc2InstanceId)

      hosts <- Future.sequence(ec2InstanceIds.map(id => cache.hostCache.get(id, () => getEc2Host(id))))
    } yield {
      val tasksByContainer = tasks.getTasks.asScala.groupBy(_.getContainerInstanceArn)

      val hostTasks =
        tasksByContainer.map {
          case (containerInstance, task) => {
            val container = containers.find(_.getContainerInstanceArn == containerInstance)
            val ec2Instance = container.flatMap(c => hosts.find(_.getInstanceId == c.getEc2InstanceId))

            HostTask(task.head, container, ec2Instance)
          }
        }

      EcsService(
        service,
        definition.getTaskDefinition,
        hostTasks.toList
      )
    }
  }

  private def getEc2Host(id: String): Future[Instance] = {
    ec2.describeInstancesAsync(new DescribeInstancesRequest().withInstanceIds(id)).
      toScalaFuture().
      map(_.getReservations.asScala.map(_.getInstances.asScala.head).head)
  }
}

case class EcsCluster(
  name: String,
  arn: String,
  serviceCount: ServiceCount
)

case class EcsClusterDetail(
  instances: List[ContainerInstanceData]
)

case class ContainerInstanceData(ec2HostId: String, serviceCount: ServiceCount)

case class ServiceCount(active: Int, pending: Int)

case class EcsService(
  service: Service,
  definition: TaskDefinition,
  tasks: List[HostTask]
)

case class HostTask(task: Task, containerInstance: Option[ContainerInstance], host: Option[Instance])