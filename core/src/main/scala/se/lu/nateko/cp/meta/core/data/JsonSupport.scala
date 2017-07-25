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
	implicit val orgFormat = jsonFormat3(Organization)
	implicit val personFormat = jsonFormat3(Person)
	implicit val objectProducerFormat = jsonFormat6(Station)

	implicit object agentFormat extends JsonFormat[Agent]{

		def write(agent: Agent): JsValue = agent match{
			case person: Person => person.toJson
			case org: Organization => org.toJson
		}

		def read(value: JsValue): Agent = value match{
			case JsObject(fields) =>
				if(fields.contains("firstName"))
					value.convertTo[Person]
				else
					value.convertTo[Organization]
			case _ =>
				deserializationError("Expected JS object representing eigher a person or an organization")
		}
	}

	implicit val dataProductionFormat = jsonFormat5(DataProduction)
	implicit val dataAcquisitionFormat = jsonFormat2(DataAcquisition)
	implicit val dataSubmissionFormat = jsonFormat3(DataSubmission)

	implicit val spatialCoverageFormat = jsonFormat3(SpatialCoverage)
	implicit val geoTrackFormat = jsonFormat1(GeoTrack)
	implicit val temporalCoverageFormat = jsonFormat2(TemporalCoverage)

	implicit val l2SpecificMetaFormat = jsonFormat3(L2OrLessSpecificMeta)
	implicit val l3SpecificMetaFormat = jsonFormat6(L3SpecificMeta)

	implicit val wdcggUploadCompletionFormat = jsonFormat3(WdcggUploadCompletion)
	implicit val ecocsvUploadCompletionFormat = jsonFormat1(TimeSeriesUploadCompletion)
	implicit val socatUploadCompletionFormat = jsonFormat2(SpatialTimeSeriesUploadCompletion)

	implicit object uploadCompletionInfoFormat extends RootJsonFormat[UploadCompletionInfo]{

		def write(uploadInfo: UploadCompletionInfo): JsValue = uploadInfo match{
			case EmptyCompletionInfo => JsObject.empty
			case wdcgg: WdcggUploadCompletion => wdcgg.toJson
			case ts: TimeSeriesUploadCompletion => ts.toJson
			case sts: SpatialTimeSeriesUploadCompletion => sts.toJson
		}

		def read(value: JsValue): UploadCompletionInfo =  value match {
			case JsObject(fields)  =>
				if(fields.isEmpty)
					EmptyCompletionInfo
				else if(fields.contains("customMetadata"))
					value.convertTo[WdcggUploadCompletion]
				else if(fields.contains("coverage"))
					value.convertTo[SpatialTimeSeriesUploadCompletion]
				else
					value.convertTo[TimeSeriesUploadCompletion]
			case _ =>
				deserializationError("Expected JS object representing upload completion info")
		}
	}

	implicit val dataObjectFormat = jsonFormat7(DataObject)

}
