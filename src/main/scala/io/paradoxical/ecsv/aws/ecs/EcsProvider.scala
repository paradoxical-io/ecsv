package io.paradoxical.ecsv.aws.ecs

import com.amazonaws.services.ec2.AmazonEC2Async
import com.amazonaws.services.ec2.model._
import com.amazonaws.services.ecs.AmazonECSAsync
import com.amazonaws.services.ecs.model._
import com.google.common.base.Suppliers
import com.google.common.cache.CacheBuilder
import com.google.inject.Inject
import io.paradoxical.ecsv.util.Futures.Implicits._
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EcsProvider @Inject()(
  ecs: AmazonECSAsync,
  ec2: AmazonEC2Async
)(implicit executionContext: ExecutionContext) {
  private lazy val clusters = Suppliers.memoizeWithExpiration[Future[List[Cluster]]](() => listClusters(), 20, TimeUnit.MINUTES)

  private lazy val fullServiceCache = CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.SECONDS).build[String, Future[Option[EcsService]]]
  private lazy val serviceCache = CacheBuilder.newBuilder().expireAfterWrite(3, TimeUnit.MINUTES).build[String, Future[Seq[Service]]]
  private lazy val containersCache = CacheBuilder.newBuilder().expireAfterWrite(3, TimeUnit.MINUTES).build[String, Future[ContainerInstance]]
  private lazy val hostCache = CacheBuilder.newBuilder().expireAfterWrite(3, TimeUnit.MINUTES).build[String, Future[Instance]]

  private def listClusters() = {
    ecs.describeClustersAsync().toScalaFuture().map(_.getClusters.asScala.toList)
  }

  def getClusters: Future[List[Cluster]] = clusters.get()

  def getServices(cluster: String): Future[Seq[Service]] = {
    serviceCache.get(cluster, () => {
      val arns = listServiceArns(cluster, root = true)

      Future.sequence(arns.grouped(10).map(group => partial(group.toList))).map(_.flatten.toSeq)
    })
  }

  def getDetailService(arn: String): Future[Option[EcsService]] = fullServiceCache.get(arn, () => fullData(arn))

  private def listServiceArns(cluster: String, prev: Seq[String] = Nil, token: Option[String] = None, root: Boolean = false): Seq[String] = {
    if (token.isEmpty && !root) {
      prev
    } else {
      val s = ecs.listServices(new ListServicesRequest().withCluster(cluster).withNextToken(token.orNull))

      listServiceArns(cluster, s.getServiceArns.asScala ++ prev, Option(s.getNextToken))
    }
  }

  def partial(services: List[String])(implicit executionContext: ExecutionContext): Future[Seq[Service]] = {
    for {
      services <- ecs.describeServicesAsync(new DescribeServicesRequest().withServices(services.asJava)).toScalaFuture
    } yield {
      services.getServices.asScala
    }
  }

  private def fullData(service: String)(implicit executionContext: ExecutionContext): Future[Option[EcsService]] = {
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

      containers <- Future.sequence(containerArns.map(arn => containersCache.get(arn, () => getContainerInstance(arn))))

      ec2InstanceIds = containers.map(_.getEc2InstanceId)

      hosts <- Future.sequence(ec2InstanceIds.map(id => hostCache.get(id, () => getEc2Host(id))))
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

case class EcsService(
  service: Service,
  definition: TaskDefinition,
  tasks: List[HostTask]
)

case class HostTask(task: Task, containerInstance: Option[ContainerInstance], host: Option[Instance])