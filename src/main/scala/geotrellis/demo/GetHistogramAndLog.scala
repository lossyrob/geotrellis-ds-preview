package geotrellis.demo

import geotrellis._
import geotrellis.statistics._
import geotrellis.statistics.FastMapHistogram
import geotrellis.statistics._

/**
 * Generate a histogram, with a few println statements thrown in to 
 * demonstrate where the work is being done.
 */
case class GetHistogramAndLog(r:Op[Raster]) extends logic.TileReducer1[Histogram] {
  type B = FastMapHistogram

  case class UntiledHistogram(r:Op[Raster]) extends Op1(r) ({
    (r) => {
      println("Executing map step on individual tile.")
      Result(List(FastMapHistogram.fromRaster(r.force)))
    }
  })

  def mapper(r:Op[Raster]):Op[List[FastMapHistogram]] = UntiledHistogram(r)
  def reducer(hs:List[FastMapHistogram]):Histogram = {
    println("Executing reduction step (combining histograms).")
    FastMapHistogram.fromHistograms(hs)
  }
}

