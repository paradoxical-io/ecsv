package io.paradoxical.ecsv.aws

import com.amazonaws.ClientConfiguration
import com.amazonaws.auth._
import com.amazonaws.client.builder.AwsAsyncClientBuilder
import com.amazonaws.regions._
import com.google.common.util.concurrent.MoreExecutors
import java.util.Collections
import java.util.concurrent.{AbstractExecutorService, TimeUnit}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

object AWSClientSettings {
  val CLIENT_TTL = Option(System.getProperty("aws.ttl")).map(_.toLong).getOrElse(20L).seconds
}

// stolen from victor klang: https://gist.github.com/viktorklang/5245161
/**
 * Convert an execution context into an execution context executor service
 */
object ExecutionContextExecutorServiceBridge {
  def apply(ec: ExecutionContext): ExecutionContextExecutorService = ec match {
    case null => throw null
    case eces: ExecutionContextExecutorService => eces
    case other => new AbstractExecutorService with ExecutionContextExecutorService {
      override def prepare(): ExecutionContext = other

      override def isShutdown = false

      override def isTerminated = false

      override def shutdown() = ()

      override def shutdownNow() = Collections.emptyList[Runnable]

      override def execute(runnable: Runnable): Unit = other execute runnable

      override def reportFailure(t: Throwable): Unit = other reportFailure t

      override def awaitTermination(length: Long, unit: TimeUnit): Boolean = false
    }
  }
}


object AwsClientFactory {
  def withCredentialsAndRegion[T](clientProvider: (AWSCredentialsProvider, String) => T): T = {
    clientProvider(new DefaultAWSCredentialsProviderChain, new DefaultAwsRegionProviderChain().getRegion)
  }

  def client[Y](implicit executionContext: ExecutionContext) = {
    new AwsClientBuilder[Y]()
  }

  class AwsClientBuilder[Client](implicit executionContext: ExecutionContext) {
    def fromBuilder[Builder <: AwsAsyncClientBuilder[Builder, Client]](builder: AwsAsyncClientBuilder[Builder, Client]): Client = {
      AwsClientFactory.withCredentialsAndRegion { (creds, region) =>
        val clientConfig = new ClientConfiguration()

        clientConfig.setConnectionTTL(AWSClientSettings.CLIENT_TTL.toMillis)

        val decoratedExecutor = MoreExecutors.listeningDecorator(ExecutionContextExecutorServiceBridge(executionContext))

        builder.
          withRegion(region).
          withCredentials(creds).
          withClientConfiguration(clientConfig).
          withExecutorFactory(() => decoratedExecutor).
          build()
      }
    }
  }
}

