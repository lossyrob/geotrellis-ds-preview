
Data Source API Preview
-----------------------

This project provides a demonstration of the new GeoTrellis data source API currently
being developed for GeoTrellis 0.9.  The new API is a major change and leap forward: it
allows you transform your geospatial data in both a much simpler object-oriented style 
as well as a functional, scala-friendly style very similar to transformations on Scala 
collections.  The semantics of distribution are much simpler than before, and some
critical issues have been resolved.

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



Distributing your operations
----------------------------

Migration notes

This is a fundamental shift from the old model, in which the primary mechanism for 
distributing operations was calling a "dispatch" method (with a cluster reference) which
distributed all child operations of a given operation.
