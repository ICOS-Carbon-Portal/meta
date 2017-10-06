package se.lu.nateko.cp.meta.core.data


sealed trait UploadCompletionInfo{
	def bytes: Long
}

case class DefaultCompletionInfo(bytes: Long) extends UploadCompletionInfo

case class WdcggUploadCompletion(bytes: Long, nRows: Int, interval: TimeInterval, customMetadata: Map[String, String]) extends UploadCompletionInfo

case class TimeSeriesUploadCompletion(bytes: Long, interval: TimeInterval) extends UploadCompletionInfo

case class SpatialTimeSeriesUploadCompletion(bytes: Long, interval: TimeInterval, coverage: GeoFeature) extends UploadCompletionInfo
