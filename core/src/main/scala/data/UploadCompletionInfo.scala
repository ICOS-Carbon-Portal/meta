package se.lu.nateko.cp.meta.core.data


sealed trait IngestionMetadataExtract

case class UploadCompletionInfo(bytes: Long, ingestionResult: Option[IngestionMetadataExtract])

case class NetCdfExtract(varInfo: Seq[VarInfo]) extends IngestionMetadataExtract

case class VarInfo(name: String, min: Double, max: Double)

case class TimeSeriesExtract(tabular: TabularIngestionExtract, nRows: Option[Int]) extends IngestionMetadataExtract

case class SpatialTimeSeriesExtract(tabular: TabularIngestionExtract, coverage: GeoFeature) extends IngestionMetadataExtract

case class TabularIngestionExtract(actualColumns: Option[Seq[String]], interval: TimeInterval)
