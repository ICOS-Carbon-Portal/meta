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
	implicit val objectProducerFormat = jsonFormat5(DataProducer)
	implicit val dataProductionFormat = jsonFormat2(DataProduction)
	implicit val dataAcquisitionFormat = jsonFormat2(DataAcquisition)
	implicit val dataSubmissionFormat = jsonFormat3(DataSubmission)

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

	implicit object dataProvenanceFormat extends JsonFormat[DataProvenance] {

		def write(prov: DataProvenance): JsValue = prov match{
			case acq: DataAcquisition => acq.toJson
			case prod: DataProduction => prod.toJson
			case subm: DataSubmission => subm.toJson
		}

		def read(value: JsValue): DataProvenance = {
			val fields = value.asJsObject("DataProvenance object expected").fields
			if(fields.contains("producer")) value.convertTo[DataAcquisition]
			else if(fields.contains("producers")) value.convertTo[DataProduction]
			else value.convertTo[DataSubmission]
		}
	}

	implicit val dataObjectFormat = jsonFormat6(DataObject)

}
