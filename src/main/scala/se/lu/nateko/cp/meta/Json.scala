package se.lu.nateko.cp.meta

import spray.json.*
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.core.CommonJsonSupport
import se.lu.nateko.cp.meta.core.crypto.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.doi.*
import scala.util.Failure
import scala.util.Success
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers.liftMarshaller
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpEntity
import se.lu.nateko.cp.meta.core.data.GeoFeature
import java.net.URI

trait CpmetaJsonProtocol extends CommonJsonSupport{
	import DefaultJsonProtocol.*

	export DefaultJsonProtocol.immSeqFormat
	export DefaultJsonProtocol.mapFormat
	export DefaultJsonProtocol.RootJsObjectFormat

	given [T: RootJsonWriter]: Conversion[T,ToResponseMarshallable] =
		ToResponseMarshallable(_)(liftMarshaller(SprayJsonSupport.sprayJsonMarshaller))

	given Conversion[JsValue,ToResponseMarshallable] =
		ToResponseMarshallable(_)(liftMarshaller(SprayJsonSupport.sprayJsValueMarshaller))

	given [T: RootJsonReader]: FromRequestUnmarshaller[T] = Unmarshaller
		.strict[HttpRequest, HttpEntity](_.entity)
		.andThen(SprayJsonSupport.sprayJsonUnmarshaller)

	given RootJsonFormat[ResourceDto] = jsonFormat3(ResourceDto.apply)
	given RootJsonFormat[LiteralValueDto] = jsonFormat2(LiteralValueDto.apply)
	given RootJsonFormat[ObjectValueDto] = jsonFormat2(ObjectValueDto.apply)

	given RootJsonFormat[MinRestrictionDto] = jsonFormat1(MinRestrictionDto.apply)
	given RootJsonFormat[MaxRestrictionDto] = jsonFormat1(MaxRestrictionDto.apply)
	given RootJsonFormat[RegexpRestrictionDto] = jsonFormat1(RegexpRestrictionDto.apply)
	given RootJsonFormat[OneOfRestrictionDto] = jsonFormat1(OneOfRestrictionDto.apply)

	given JsonFormat[ValueDto] with{
		override def write(dto: ValueDto) = dto match
			case dto: LiteralValueDto => dto.toJson.pluss("type" -> "literal")
			case dto: ObjectValueDto => dto.toJson.pluss("type" -> "object")

		override def read(value: JsValue) = ???
	}

	given JsonFormat[DataRestrictionDto] with{
		override def write(dto: DataRestrictionDto) = dto match{
			case dto: MinRestrictionDto => dto.toJson.pluss("type" -> "minValue")
			case dto: MaxRestrictionDto => dto.toJson.pluss("type" -> "maxValue")
			case dto: RegexpRestrictionDto => dto.toJson.pluss("type" -> "regExp")
			case dto: OneOfRestrictionDto => dto.toJson.pluss("type" -> "oneOf")
		}
		override def read(value: JsValue) = ???
	}

	given RootJsonFormat[DataRangeDto] = jsonFormat2(DataRangeDto.apply)
	given RootJsonFormat[CardinalityDto] = jsonFormat2(CardinalityDto.apply)
	given RootJsonFormat[DataPropertyDto] = jsonFormat3(DataPropertyDto.apply)
	given RootJsonFormat[ObjectPropertyDto] = jsonFormat2(ObjectPropertyDto.apply)

	given JsonFormat[PropertyDto] with{
		override def write(dto: PropertyDto) = dto match{
			case dto: DataPropertyDto => dto.toJson.pluss("type" -> "dataProperty")
			case dto: ObjectPropertyDto => dto.toJson.pluss("type" -> "objectProperty")
		}
		override def read(value: JsValue) = ???
	}

	given JsonFormat[GeoCoverage] with
		def write(spatialObject: GeoCoverage) = spatialObject match
			case feature: GeoFeature => feature.toJson
			case uri: URI => uri.toJson
			case jsonString: String => JsString(jsonString)
		def read(json: JsValue): GeoCoverage = json match
			case obj: JsObject => obj.convertTo[GeoFeature]
			case JsString(str) =>
				try new URI(str)
				catch case _ => GeoJsonString.unsafe(str)
			case _ => deserializationError(s"Error parsing GeoFeature from JSON, expected an object or a string ${json.getClass.getName}")

	given RootJsonFormat[ClassDto] = jsonFormat2(ClassDto.apply)
	given RootJsonFormat[ClassInfoDto] = jsonFormat3(ClassInfoDto.apply)
	given RootJsonFormat[IndividualDto] = jsonFormat3(IndividualDto.apply)
	given RootJsonFormat[UpdateDto] = jsonFormat4(UpdateDto.apply)
	given RootJsonFormat[ReplaceDto] = jsonFormat4(ReplaceDto.apply)

	given RootJsonFormat[Doi] with{

			override def write(doi: Doi): JsValue = JsString(doi.toString)

			override def read(value: JsValue): Doi = value match {
				case JsString(doiStr) =>
					Doi.parse(doiStr) match {
						case Success(doi) => doi
						case Failure(exc) => deserializationError("Bad DOI", exc)
					}
				case _ => deserializationError("Error parsing a DOI from JSON, expected a string, got " + value.getClass.getName)
		}
	}

	given RootJsonFormat[DataProductionDto] = jsonFormat7(DataProductionDto.apply)
	given RootJsonFormat[StationTimeSeriesDto] = jsonFormat9(StationTimeSeriesDto.apply)
	given RootJsonFormat[SpatioTemporalDto] = jsonFormat9(SpatioTemporalDto.apply)
	given RootJsonFormat[ReferencesDto] = jsonFormat6(ReferencesDto.apply)
	given RootJsonFormat[DataObjectDto] = jsonFormat8(DataObjectDto.apply)
	given RootJsonFormat[DocObjectDto] = jsonFormat9(DocObjectDto.apply)

	given RootJsonFormat[ObjectUploadDto] with
		override def write(umd: ObjectUploadDto) = umd match{
			case data: DataObjectDto => data.toJson
			case doc: DocObjectDto => doc.toJson
		}
		override def read(value: JsValue): ObjectUploadDto = {
			val obj = value.asJsObject("Expected UploadMetadataDto to be a JSON object")
			if(obj.fields.contains("objectSpecification")) obj.convertTo[DataObjectDto]
			else obj.convertTo[DocObjectDto]
		}

	given RootJsonFormat[StaticCollectionDto] = jsonFormat8(StaticCollectionDto.apply)

	given RootJsonWriter[UploadDto] with{
		override def write(dto: UploadDto) = dto match {
			case oud: ObjectUploadDto => oud.toJson
			case scd: StaticCollectionDto => scd.toJson
		}
	}

	given RootJsonFormat[UserId] = jsonFormat1(UserId.apply)

	given RootJsonFormat[FileDeletionDto] = jsonFormat2(FileDeletionDto.apply)
	given RootJsonFormat[LabelingUserDto] = jsonFormat9(LabelingUserDto.apply)
	given RootJsonFormat[LabelingStatusUpdate] = jsonFormat3(LabelingStatusUpdate.apply)

	given RootJsonFormat[SubmitterProfile] = jsonFormat5(SubmitterProfile.apply)
}
