package geotrellis.demo

import akka.kernel.Bootable
import akka.actor.{ Props, Actor, ActorSystem }
import com.typesafe.config.ConfigFactory

import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings
import akka.routing.ConsistentHashingRouter
import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings
import akka.cluster.routing.AdaptiveLoadBalancingRouter
import akka.cluster.routing.HeapMetricsSelector
import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings
import akka.cluster.routing.AdaptiveLoadBalancingRouter
import akka.cluster.routing.SystemLoadAverageMetricsSelector
import geotrellis.rest._
import geotrellis.process._
import geotrellis.admin._
import geotrellis.admin.Json._
import geotrellis.rest.GeoTrellis
import geotrellis.rest.GeoTrellisConfig


// Run 'RemoteServer' in different sbt terminals, like the following.
// ./sbt
// project dev
// run 2551  (to listen on port 2551)

// Each time you run remote server, use a distinct port.


class RemoteServerApplication extends Bootable {
  // The client will identify this server as a candidate for work
  // by id, which is set as "remoteServer" in the client's configuration.
  val id = "remoteServer"
  val config = ServerConfig.init()
  GeoTrellis.setup(config.geotrellis, id)

  val server = GeoTrellis.server

  def startup() {
  }

  def shutdown() {
    server.shutdown()
  }
}

object RemoteServer {
  def main(args: Array[String]) {
  /* 
   if (args.nonEmpty)
     System.setProperty("akka.remote.netty.tcp.port", args(0))
   else
     System.setProperty("akka.remote.netty.tcp.port", "2551")
   */
    new RemoteServerApplication
    println("Started GeoTrellis remote server.")
  }
}
