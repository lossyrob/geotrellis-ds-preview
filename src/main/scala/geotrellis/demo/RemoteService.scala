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
import geotrellis.source._
import geotrellis.statistics.Histogram

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

//import javax.ws.rs.core._
import javax.ws.rs._

import geotrellis.feature.Point
import geotrellis._
import geotrellis.raster.op._
import geotrellis.rest.op._
import geotrellis.admin._
import geotrellis.admin.Json._
import geotrellis.rest._
import javax.ws.rs.core.Response

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
   // val uncachedRaster = RemoteOperation(io.LoadRaster("mtsthelens_tiled"),app.router)
    val uncachedRaster = RemoteOperation(Literal(4).map(_ + 1),app.router)

    //val histOp = GetHistogramAndLog(uncachedRaster)
    //histOp.limit = 5000

    //val histOp = Literal(3)

    val histOp = uncachedRaster
    RemoteClient.testSerialization(histOp, GeoTrellis.server)
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

    //Response.ok(s"done in $elapsed").`type`("text/html").build()
    OK("{ 'elapsed': $elapsed }").build
  }
}

@Path("/min") 
class MinResource {
  @GET
  def min (
    @QueryParam("local")
    @DefaultValue("false")
    local:Boolean,

    @QueryParam("json")
    @DefaultValue("false")
    json:Boolean
  ) = {

    val app = RemoteClient.app

    val src = if (local) 
      MinResource.localSource
    else 
      MinResource.distributedSource(app.router)

    val history = GeoTrellis.server.getSource(src) match {
      case Complete(value, success) => 
        println(success.toJson)
        success
      case Error(msg,failure) =>
        println(msg)
        println(failure)
        failure
    }
    
    if (json) 
      OK.json(history.toJson).build
    else
      OK(s"<pre>${history.toString}</pre>").mimeType("text/html;charset=utf-8").build
     
  }
}


object MinResource {
  val getMinValueFromHistogram = 
    RasterSource("mtsthelens_tiled_cached")
      .localAdd(3)
      .histogram
      .map( (h:Histogram) => h.getMinValue)

  def distributedSource(cluster:ActorRef) = 
    getMinValueFromHistogram
      .distribute(cluster)
      .converge
      .map(_.reduce(math.min(_,_)))

  val localSource =
    getMinValueFromHistogram
      .converge
      .map(_.reduce(math.min(_,_)))
}


class RemoteClientApplication extends Bootable {
  val id = "localServer"
  val config = ServerConfig.init()
  GeoTrellis.setup(config.geotrellis, id)
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
    if (args.nonEmpty) System.setProperty("akka.remote.netty.tcp.port", args(0))
    println("[CLIENT] Connecting to cluster.")

    // wait until client joins the cluster, and then execute onClusterJoin
    Cluster(server.system).registerOnMemberUp (
      this.onClusterJoin(server)
    )
  }

  // This method excecutes when the client has successfully connected to the cluster.
  def onClusterJoin(server:Server) {
    WebRunner.run()
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
