package se.lu.nateko.cp.meta.core.data


sealed trait IngestionMetadataExtract

final case class UploadCompletionInfo(bytes: Long, ingestionResult: Option[IngestionMetadataExtract])

final case class NetCdfExtract(varInfo: Seq[VarInfo]) extends IngestionMetadataExtract

final case class VarInfo(name: String, min: Double, max: Double)

final case class TimeSeriesExtract(tabular: TabularIngestionExtract, nRows: Option[Int]) extends IngestionMetadataExtract

final case class SpatialTimeSeriesExtract(tabular: TabularIngestionExtract, coverage: GeoFeature) extends IngestionMetadataExtract

final case class TabularIngestionExtract(actualColumns: Option[Seq[String]], interval: TimeInterval)
