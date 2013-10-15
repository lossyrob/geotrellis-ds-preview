package geotrellis.demo

import akka.kernel.Bootable
import scala.util.Random

import com.typesafe.config.ConfigFactory
import akka.actor.{ ActorRef, Props, Actor, ActorSystem }
import geotrellis.raster.op._
import geotrellis.process._
import geotrellis._
import geotrellis.raster._
import geotrellis.statistics.op._

import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings
import akka.cluster.routing.AdaptiveLoadBalancingRouter
import akka.cluster.routing.HeapMetricsSelector
import akka.cluster.routing.AdaptiveLoadBalancingRouter
import akka.cluster.routing.SystemLoadAverageMetricsSelector
import akka.kernel.Bootable
import akka.actor.{ Props, Actor, ActorSystem }
import com.typesafe.config.ConfigFactory

import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings
import akka.routing.ConsistentHashingRouter
import akka.routing.FromConfig
import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings
import akka.cluster.routing.AdaptiveLoadBalancingRouter
import akka.cluster.routing.HeapMetricsSelector
import akka.cluster.routing.ClusterRouterConfig
import akka.cluster.routing.ClusterRouterSettings
import akka.cluster.routing.AdaptiveLoadBalancingRouter
import akka.cluster.routing.SystemLoadAverageMetricsSelector

import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberUp

import geotrellis.process._
import akka.serialization._

import javax.ws.rs.core._
import javax.ws.rs._

import geotrellis.feature.Point
import geotrellis._
import geotrellis.raster.op._
import geotrellis.rest.op._
import geotrellis.admin._
import geotrellis.admin.Json._
import geotrellis.rest.GeoTrellis
import geotrellis.rest.GeoTrellisConfig
import com.vividsolutions.jts.geom.{Point => JtsPoint}

@Path("/cluster")
class Resource {
  @GET
  def cluster (
    @QueryParam("local")
    local: String
  ) = {
    val app = RemoteClient.app
   // var carbonLocation = "/home/jmarcus/projects/github/cluster-demo/src/main/scala/geotrellis/demo/src/main/resources/tiles/"
    val uncachedRaster = io.LoadRaster("albers_Wetlands")
    val histOp = GetHistogramAndLog(uncachedRaster)
    histOp.limit = 5000

    val start = System.currentTimeMillis
    val op = if (local == "true") {
      println(" == Executing locally")
      histOp
    } else {
       println(" == Sending op for remote execution.")
      histOp.dispatch(app.router) 
    }
    val result = GeoTrellis.run(op)
    val elapsed = System.currentTimeMillis - start
    println(s"result: $result")
    println(s" ==== completed.  elapsed time: $elapsed\n")

    Response.ok(s"done in $elapsed").`type`("text/html").build()
  }
}

//Remove Bootable entirely?
class RemoteClientApplication extends Bootable {
  val server = GeoTrellis.server
  val router = server.system.actorOf(
      Props.empty.withRouter(FromConfig),
      name = "clusterRouter")

  def startup() {
  }

  def shutdown() {
    server.shutdown()
  }
}


object RemoteClient {

  val app = new RemoteClientApplication
  val server = app.server

  def main(args: Array[String]) {
    println("Attempting to connect to cluster.")
    if (args.nonEmpty) System.setProperty("akka.remote.netty.port", args(0))
  }

  def testSerialization(remoteOp:AnyRef, server:Server) {
    val serialization = SerializationExtension(server.system)
    val serializer = serialization.findSerializerFor(remoteOp)
    val bytes = serializer.toBinary(remoteOp)
    val back = serializer.fromBinary(bytes, manifest = None)
    assert(back == remoteOp)
  }
}

case class HelloWorldOp[String](s:Op[String]) extends Op1(s)({
  s => {
    println("Executing load tileset operation.")
    Result(s)
  }
})
