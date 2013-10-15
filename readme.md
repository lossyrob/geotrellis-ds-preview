
Data Source API
---------------

The GeoTrellis data source API allows you to transform your geospatial data in both a 
object-oriented style as well as a functional, scala-friendly style very 
similar to transformations on Scala collections.

Usage
-----

```scala
val r = io.LoadRasterSource("myraster")
        .localAdd(4) ## add 4 to every cell
		.focalMin(Square(4)) ## apply a focal min operation
		.histogram
		.distribute(cluster)
```		
Technical notes
---------------

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



Distributing your operations
----------------------------

Migration notes

This is a fundamental shift from the old model, in which the primary mechanism for 
distributing operations was calling a "dispatch" method (with a cluster reference) which
distributed all child operations of a given operation.
