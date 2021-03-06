
Data Source API Preview
-----------------------

This project provides a demonstration of the new GeoTrellis data source API currently
being developed for GeoTrellis 0.9.  The new API is a major change and leap forward: it
allows you transform your geospatial data in both a much simpler object-oriented style 
as well as a functional, scala-friendly style very similar to transformations on Scala 
collections.  The semantics of distribution are much simpler than before, and some
critical issues have been resolved.

Running the Preview Demo
------------------------

This demo project implements a webservice that distributes a raster operation
over a cluster and returns diagnostic information about the operation execution.

Each node in a GeoTrellis cluster (including the client application) needs to know
an ip address of a node on the cluster.  That node is called the "seed node" but it
can be any node -- there's nothing special about it, and you can include more than
one seed node.

When starting up the demo, you should start up some number of geotrellis "server" 
processes (either on a single machine or on different machines) under separate sbt 
sessions and then one "client" server, which is the server that will create a webservice
and send work to the other nodes.

To start up a server process on a dedicated machine, run:

```bash
  ./sbt -Dgeotrellis.cluster_seed="clusterseed" -Dgeotrellis.hostname="`hostname`" "run-main geotrellis.demo.RemoteServer"
```
To start up the client/webservice process on a dedicated machine, with a http service on port 8888, run:
```bash
./sbt -Dgeotrellis.cluster_seed="clusterseed" -Dgeotrellis.hostname="`hostname`" -Dgeotrellis.port=8888 "run-main geotrel
lis.demo.RemoteClient"
```
but replace "clusterseed" with the hostname or ip address of one your nodes, or add a entry in /etc/hosts.  Set geotrellis.hostname to the hostname or ip address that the other nodes can use.

If you want to run the demo on a single machine, you'll need to change the port each
node listens to for remote communication (akka_port).  By default, the cluster seed port is 2551 (although you can change it by setting -Dgeotrellis.cluster_seed_port) so you'll need at least one node to listen to port 2551.  For example,

```bash
  ## start up one node
  ./sbt -Dgeotrellis.akka_port="2551" "run-main geotrellis.demo.RemoteServer"
 
  ## start up a second node (in a separate terminal)
  ./sbt -Dgeotrellis.akka_port="2552" "run-main geotrellis.demo.RemoteServer"

  ## start up the web service on port 8888
  ./sbt -Dgeotrellis.akka_port="2553" -Dgeotrellis.port=8888 "run-main geotrellis.demo.RemoteClient"
```

Once the webservice (RemoteClient) process connects to the cluster, it will start up 
its embedded webserver on port 8888 (or whatever port you configure).

If you are running it locally, you can now hit http://localhost:8888/gt/min to see an
ascii tree of the execution it has executed.  If you add ?local=true to the URL, it
will run the operation locally.  If you add ?json=true, you'll get a json representation
of the operation eexcution.

Note that the configuration of the demo service won't run "remote" operations on the
client node: you can change that by changing "allow-local-routees = off" to on.

Introduction to the Data Source API
-----------------------------------

Here's a code example that describes a raster operation using the new API.

```scala
val r = io.LoadRasterSource("myraster")
          .localAdd(4) // add 4 to every cell
		  .focalMin(Square(2)) // apply a focal min operation
		  .histogram // get a histogram
		  .distribute(cluster) // do this work over a cluster
		  // and so on
```	

The first line,

```scala
val r = io.LoadRasterSource("myraster")
```

loads a raster named "myraster" from the server catalog.  Or, more precisely, we are
defining a data pipeline that we will execute in the future -- no data is actually
loaded at this stage.

The second line is an example of a simple map algebra operation: add four to every
cell of the raster.  (This isn't a practical real world example.)

The third line performs a focal minimum operation with a neighborhood of 2 pixels --
every pixel is replaced with the lowest value found in its neighbors, with the 
neighborhood limited to 2 pixels on every side.

The fourth line asks for the histogram of that raster, and the fifth line asks for
the entire operation to be distributed over the GeoTrellis cluster (the cluster variable
has been set before this snippit).

Getting a little deeper
-----------------------

So, what's being distributed?  

Every data source can be thought of in two ways: it is a collection of individual
data elements, and it also represents the entire collection or something that can be
built from those elements.

What does that mean?  Imagine a data source -- like a raster -- that can be divided
up into smaller chunks.  A 10k by 10k raster (a grid of 10k columns & 10k rows) can be
divided into, for example, 100 tiles that are each 1000x1000 rasters.

When possible, all transformations are defined as work on each chunk -- so each chunk 
can be executed in parallel.  Each transformation that can happen on independent tiles
will produce an independent tile -- on a different machine, if distributed -- and
produces (such as a histogram in the example above) independent results.

In the example above, the first operations are actually transformations on individual 
tiles -- and when we ask for a histogram, we in fact have a chunked up histogram
distributed across our cluster.  The individual elements are histograms of each tile.
The histogram data source represents a single histogram as well, as GeoTrellis knows
how to combine the individual histograms if necessary.  But it's best to avoid the
combination step unless necessary.  When you need to specifically act on the combined
value, the converge() method will combine the data source on a single machine -- and
you can continue to chain transformations on that converged data product.


Task Parallelism
----------------

GeoTrellis, by default, will use task parallelism to execute independent tasks at the
same time, but the data source API doesn't currently provide a custom method for 
distributing those tasks.  The distribute() command allows you to distribute work
that can be run in parallel due to data parallelism: the data is chunked up, and we
can operate on the datum independently.

While the data source API does not currently provide a method for custom task 
distribution, the old dispatch()/DispatchedOperation mechanism is still in place on
Operations which can be used if necessary.  At this stage, it's not clear that
it's useful and necessary, and may be removed in future versions.

Technical Notes on DataSource
-----------------------------

A data source is similar to a scala collection such as Seq or List -- it can be thought 
of as an ordered sequence of elements of a particular type, and each element can be 
transformed individually.  A data source is also similar to a Future, in that it
represents a computation that will happen in the future -- and a data pipeline can be
created by providing functions to transform the result of a previous transformation.

A data source, however, has one critical additional piece of functionality.  If a data
source can be thought of like a Seq[E], where E is the type of the element in its 
sequence, then what is the type of the Future -- what kind of value does it return?
A data source extends the trait DataSource[E,V] where E is the kind of element in its 
sequence and V is the kind of value it returns.  In a simple case, a datasource might
be of type DataSource[E,Seq[E]] -- it is a sequence of elements of type E, and overall 
it returns a sequence of those results.  But, for example, a RasterSource -- which
represents a raster layer which can be transformed through a variety of map algebra or
raster operations -- extends the type DataSource[Raster,Raster], because the elements
are individual tiles that make up a single raster dataset.  The overall result is the
whole raster that can be built from combining those rasters.  The overall result might 
never be built, but the datasource will pass along additional information necessary to
produce its value or result from its sequence.

EC2 Notes
---------

Here are some notes about setting this up manually on EC2.

0) Set up a security group that will allow your nodes to communicate with each other (e.g. ports 2551-25xx) and provide you access to the web interface of the client.

1) Start up a first AMI and check out this project, or use a pre-built AMI called "geotrellis-ds-demo" that you can use in region us-east-1: ami-e37e2c8a.

2) Login to seed node, and get its internal ip:
```bash
export SEEDIP=`curl http://169.254.169.254/latest/meta-data/local-ipv4`
echo $SEEDIP
```

3) Start a client (or server) on the seed node:
```bash
cd geotrellis-ds-preview
./run_client_ec2.sh
./sbt -Dgeotrellis.cluster_seed="$SEEDIP" -Dgeotrellis.port=8080-Dgeotrellis.hostname="$SEEDIP" "run-main geotrellis.demo.RemoteClient"
```  
  
  
You should see output that looks like this:
```
ubuntu@ip-10-212-121-203:~/geotrellis-ds-preview$ ./run_client_ec2.sh
  % Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
                                 Dload  Upload   Total   Spent    Left  Speed
100    14  100    14    0     0   5357      0 --:--:-- --:--:-- --:--:--  7000
Detected sbt version 0.12.0
Using /home/ubuntu/.sbt/0.12.0 as sbt dir, -sbt-dir to override.
[info] Set current project to Geotrellis Cluster Demo (in build file:/home/ubuntu/geotrellis-ds-preview/)
[warn] Potentially incompatible versions of dependencies of {file:/home/ubuntu/geotrellis-ds-preview/}default-b63795:
[warn]    org.scala-lang: 2.10.2, 2.10.1
[info] Running geotrellis.demo.RemoteClient
[INFO] [10/16/2013 19:58:47.041] [run-main] [Remoting] Starting remoting
[INFO] [10/16/2013 19:58:47.518] [run-main] [Remoting] Remoting started; listening on addresses :[akka.tcp://GeoTrellis@10.212.121.203:2551]
[INFO] [10/16/2013 19:58:47.544] [run-main] [Cluster(akka://GeoTrellis)] Cluster Node [akka.tcp://GeoTrellis@10.212.121.203:2551] - Starting up...
[INFO] [10/16/2013 19:58:47.654] [run-main] [Cluster(akka://GeoTrellis)] Cluster Node [akka.tcp://GeoTrellis@10.212.121.203:2551] - Registered cluster JMX MBean [akka:type=Cluster]
[INFO] [10/16/2013 19:58:47.654] [run-main] [Cluster(akka://GeoTrellis)] Cluster Node [akka.tcp://GeoTrellis@10.212.121.203:2551] - Started up successfully
[CLIENT] Connecting to cluster.
[INFO] [10/16/2013 19:58:47.757] [GeoTrellis-akka.actor.default-dispatcher-4] [Cluster(akka://GeoTrellis)] Cluster Node [akka.tcp://GeoTrellis@10.212.121.203:2551] - Node [akka.tcp://GeoTrellis@10.212.121.203:2551] is JOINING, roles []
[INFO] [10/16/2013 19:58:48.742] [GeoTrellis-akka.actor.default-dispatcher-12] [Cluster(akka://GeoTrellis)] Cluster Node [akka.tcp://GeoTrellis@10.212.121.203:2551] - Leader is moving node [akka.tcp://GeoTrellis@10.212.121.203:2551] to [Up]

        --=== GEOTRELLIS SERVER ===--

[GEOTRELLIS]  Including Admin Site...
[GEOTRELLIS]  Starting server on port 8080.
[GEOTRELLIS]    Including package geotrellis.admin.services
[GEOTRELLIS]    Including package geotrellis.demo
```

4) Launch new server instances with AMI.  Use the following as user data, with 10.212.121.203 replaced with your seed ip:

```bash
#!/bin/bash

export CLUSTER_SEED=10.212.121.203

sudo su - ubuntu
cd /home/ubuntu/geotrellis-ds-preview
./sbt -Dgeotrellis.cluster_seed="$CLUSTER_SEED" -Dgeotrellis.port=8888  -Dgeotrellis.hostname="`curl http://169.254.169.254/latest/meta-data/local-ipv4`" "run-main geotrellis.demo.RemoteServer"
```


5) You should see debugging logging on the client/seed node as the other instances come up.  Hit the external ip address of your cluster node that's also running the web client, and you can see how the work is spread out over the nodes:

http://domain:8080/min
