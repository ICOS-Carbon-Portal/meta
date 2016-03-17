package se.lu.nateko.cp.meta.core.data


sealed trait UploadCompletionInfo

case object EmptyCompletionInfo extends UploadCompletionInfo

case class WdcggUploadCompletion(nRows: Int, interval: TimeInterval, customMetadata: Map[String, String]) extends UploadCompletionInfo