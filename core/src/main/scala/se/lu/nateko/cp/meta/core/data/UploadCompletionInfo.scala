package se.lu.nateko.cp.meta.core.data


sealed trait IngestionMetadataExtract

case class UploadCompletionInfo(bytes: Long, ingestionResult: Option[IngestionMetadataExtract])

case class WdcggUploadCompletion(nRows: Int, interval: TimeInterval, customMetadata: Map[String, String]) extends IngestionMetadataExtract

case class TimeSeriesUploadCompletion(interval: TimeInterval) extends IngestionMetadataExtract

case class SpatialTimeSeriesUploadCompletion(interval: TimeInterval, coverage: GeoFeature) extends IngestionMetadataExtract
