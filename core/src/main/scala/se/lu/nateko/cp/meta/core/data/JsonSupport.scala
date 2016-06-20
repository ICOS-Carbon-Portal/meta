package se.lu.nateko.cp.meta.core.data

import se.lu.nateko.cp.meta.core.CommonJsonSupport
import spray.json._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

object JsonSupport extends CommonJsonSupport{

	import Sha256Sum.sha256sumFormat

	implicit val uriResourceFormat = jsonFormat2(UriResource)
	implicit val dataObjectSpecFormat = jsonFormat4(DataObjectSpec)


	implicit val dataThemeFormat = enumFormat(DataTheme)

	implicit val positionFormat = jsonFormat2(Position)
	implicit val objectProducerFormat = jsonFormat6(Station)
	implicit val dataProductionFormat = jsonFormat4(DataProduction)
	implicit val dataAcquisitionFormat = jsonFormat2(DataAcquisition)
	implicit val dataSubmissionFormat = jsonFormat3(DataSubmission)

	implicit val spatialCoverageFormat = jsonFormat3(SpatialCoverage)
	implicit val temporalCoverageFormat = jsonFormat2(TemporalCoverage)

	implicit val l2SpecificMetaFormat = jsonFormat2(L2OrLessSpecificMeta)
	implicit val l3SpecificMetaFormat = jsonFormat6(L3SpecificMeta)

	implicit val wdcggUploadCompletionFormat = jsonFormat3(WdcggUploadCompletion)

	implicit object uploadCompletionInfoFormat extends RootJsonFormat[UploadCompletionInfo]{

		def write(uploadInfo: UploadCompletionInfo): JsValue = uploadInfo match{
			case EmptyCompletionInfo => JsObject.empty
			case wdcgg: WdcggUploadCompletion => wdcgg.toJson
		}

		def read(value: JsValue): UploadCompletionInfo =  value match {
			case JsObject(fields) if(fields.isEmpty) =>
				EmptyCompletionInfo
			case _: JsObject =>
				value.convertTo[WdcggUploadCompletion]
			case _ =>
				deserializationError("Expected JS object representing upload completion info")
		}
	}

	implicit val dataObjectFormat = jsonFormat7(DataObject)

}
