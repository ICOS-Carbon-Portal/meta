package se.lu.nateko.cp.meta.core.data


sealed trait IngestionMetadataExtract

case class UploadCompletionInfo(bytes: Long, ingestionResult: Option[IngestionMetadataExtract])

case class WdcggUploadCompletion(tabular: TabularIngestionExtract, nRows: Int, customMetadata: Map[String, String]) extends IngestionMetadataExtract

case class TimeSeriesUploadCompletion(tabular: TabularIngestionExtract, nRows: Option[Int]) extends IngestionMetadataExtract

case class SpatialTimeSeriesUploadCompletion(tabular: TabularIngestionExtract, coverage: GeoFeature) extends IngestionMetadataExtract

case class TabularIngestionExtract(actualColumns: Option[Seq[String]], interval: TimeInterval)
