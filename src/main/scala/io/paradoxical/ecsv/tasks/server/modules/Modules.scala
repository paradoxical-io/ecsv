package io.paradoxical.ecsv.tasks.server.modules

import com.amazonaws.services.ec2.{AmazonEC2Async, AmazonEC2AsyncClientBuilder}
import com.amazonaws.services.ecs.{AmazonECSAsync, AmazonECSAsyncClientBuilder}
import com.google.inject.{Module, Provides}
import com.twitter.inject.TwitterModule
import io.paradoxical.ecsv.aws.AwsClientFactory
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

object Modules {
  def apply()(implicit executionContext: ExecutionContext): List[Module] = {
    List(
      AwsModule,
      new ExecutionContextModule()
    )
  }
}

class ExecutionContextModule(implicit executionContext: ExecutionContext) extends TwitterModule {
  @Provides
  @Singleton
  def ctx = executionContext
}

object AwsModule extends TwitterModule {
  @Provides
  @Singleton
  def ecs(implicit executionContext: ExecutionContext): AmazonECSAsync = {
    AwsClientFactory.client[AmazonECSAsync].fromBuilder(AmazonECSAsyncClientBuilder.standard())
  }

  @Provides
  @Singleton
  def ec2(implicit executionContext: ExecutionContext): AmazonEC2Async = {
    AwsClientFactory.client[AmazonEC2Async].fromBuilder(AmazonEC2AsyncClientBuilder.standard())
  }
}
