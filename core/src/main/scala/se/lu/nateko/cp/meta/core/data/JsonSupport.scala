package se.lu.nateko.cp.meta.core.data

import se.lu.nateko.cp.meta.core.CommonJsonSupport
import spray.json._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

object JsonSupport extends CommonJsonSupport{

	import Sha256Sum.sha256sumFormat

	implicit val uriResourceFormat = jsonFormat2(UriResource)
	implicit val dataObjectSpecFormat = jsonFormat5(DataObjectSpec)


	implicit val dataThemeFormat = enumFormat(DataTheme)
	implicit val orgClassFormat = enumFormat(OrganizationClass)

	implicit val positionFormat = jsonFormat2(Position)
	implicit val orgFormat = jsonFormat2(Organization)
	implicit val objectProducerFormat = jsonFormat6(Station)
	implicit val dataProductionFormat = jsonFormat5(DataProduction)
	implicit val dataAcquisitionFormat = jsonFormat2(DataAcquisition)
	implicit val dataSubmissionFormat = jsonFormat3(DataSubmission)

	implicit val spatialCoverageFormat = jsonFormat3(SpatialCoverage)
	implicit val temporalCoverageFormat = jsonFormat2(TemporalCoverage)

	implicit val l2SpecificMetaFormat = jsonFormat3(L2OrLessSpecificMeta)
	implicit val l3SpecificMetaFormat = jsonFormat6(L3SpecificMeta)

	implicit val wdcggUploadCompletionFormat = jsonFormat3(WdcggUploadCompletion)
	implicit val ecocsvUploadCompletionFormat = jsonFormat1(EcoCsvUploadCompletion)

	implicit object uploadCompletionInfoFormat extends RootJsonFormat[UploadCompletionInfo]{

		def write(uploadInfo: UploadCompletionInfo): JsValue = uploadInfo match{
			case EmptyCompletionInfo => JsObject.empty
			case wdcgg: WdcggUploadCompletion => wdcgg.toJson
			case ecocsv: EcoCsvUploadCompletion => ecocsv.toJson
		}

		def read(value: JsValue): UploadCompletionInfo =  value match {
			case JsObject(fields)  =>
				if(fields.isEmpty)
					EmptyCompletionInfo
				else if(fields.contains("customMetadata"))
					value.convertTo[WdcggUploadCompletion]
				else
					value.convertTo[EcoCsvUploadCompletion]
			case _ =>
				deserializationError("Expected JS object representing upload completion info")
		}
	}

	implicit val dataObjectFormat = jsonFormat7(DataObject)

}
