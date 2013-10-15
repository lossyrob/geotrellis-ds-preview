name := "Geotrellis Cluster Demo"

scalaVersion := "2.10.0"

scalacOptions ++= Seq("-deprecation", "-unchecked", "-optimize")

parallelExecution := false

libraryDependencies ++= Seq(
    "javax.media" % "jai_core" % "1.1.3" from "http://repo.opengeo.org/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar",
    "com.azavea.geotrellis" %% "geotrellis" % "0.9.0-20131015-SNAPSHOT",
    "com.azavea.geotrellis" %% "geotrellis-server" % "0.9.0-SNAPSHOT"
)

resolvers ++= Seq(Resolver.sonatypeRepo("releases"),
                  Resolver.sonatypeRepo("snapshots"),
                  "Geotools" at "http://download.osgeo.org/webdav/geotools/")

//mainClass in (Compile, run) := Some("geotrellis.rest.WebRunner")
