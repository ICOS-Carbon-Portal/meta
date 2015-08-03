package se.lu.nateko.cp.meta

import spray.httpx.SprayJsonSupport
import spray.json._
import java.net.URI
import java.net.URISyntaxException

object CpmetaJsonProtocol extends DefaultJsonProtocol with SprayJsonSupport{

	private implicit class ExtendableJsObj(val js: JsValue) extends AnyVal{
		def +(kv: (String, String)): JsObject = {
			val jsKv: (String, JsValue) = (kv._1, JsString(kv._2))
			JsObject(js.asJsObject.fields + jsKv)
		}
	}

	implicit object UriJsonFormat extends RootJsonFormat[URI]{

		def read(value: JsValue) = value match{
			case JsString(uri) => try{
					new URI(uri)
				}catch{
					case err: Throwable => deserializationError(s"Could not parse URI from$uri", err)
				}
			case _ => deserializationError("URI string expected")
		}

		def write(uri: URI) = JsString(uri.toString)
	}

	implicit val resourceDtoFormat = jsonFormat3(ResourceDto)
	implicit val literalValueDtoFormat = jsonFormat2(LiteralValueDto)
	implicit val objectValueDtoFormat = jsonFormat2(ObjectValueDto)

	implicit val minRestrictionDtoFormat = jsonFormat1(MinRestrictionDto)
	implicit val maxRestrictionDtoFormat = jsonFormat1(MaxRestrictionDto)
	implicit val regexpRestrictionDtoFormat = jsonFormat1(RegexpRestrictionDto)
	implicit val oneOfRestrictionDtoFormat = jsonFormat1(OneOfRestrictionDto)

	implicit object ValueDtoFormat extends JsonFormat[ValueDto]{
		override def write(dto: ValueDto) = dto match{
			case dto: LiteralValueDto => dto.toJson + ("type" -> "literal")
			case dto: ObjectValueDto => dto.toJson + ("type" -> "object")
		}
		override def read(value: JsValue) = ???
	}

	implicit object RestrictionDtoFormat extends JsonFormat[DataRestrictionDto]{
		override def write(dto: DataRestrictionDto) = dto match{
			case dto: MinRestrictionDto => dto.toJson + ("type" -> "minValue")
			case dto: MaxRestrictionDto => dto.toJson + ("type" -> "maxValue")
			case dto: RegexpRestrictionDto => dto.toJson + ("type" -> "regExp")
			case dto: OneOfRestrictionDto => dto.toJson + ("type" -> "oneOf")
		}
		override def read(value: JsValue) = ???
	}

	implicit val dataRangeDtoFormat = jsonFormat2(DataRangeDto)
	implicit val cardinalityDtoFormat = jsonFormat2(CardinalityDto)
	implicit val dataPropertyDtoFormat = jsonFormat3(DataPropertyDto)
	implicit val objectPropertyDtoFormat = jsonFormat3(ObjectPropertyDto)

	implicit object PropertyDtoFormat extends JsonFormat[PropertyDto]{
		override def write(dto: PropertyDto) = dto match{
			case dto: DataPropertyDto => dto.toJson + ("type" -> "dataProperty")
			case dto: ObjectPropertyDto => dto.toJson + ("type" -> "objectProperty")
		}
		override def read(value: JsValue) = ???
	}
	
	implicit val classDtoFormat = jsonFormat2(ClassDto)
	implicit val individualDtoFormat = jsonFormat3(IndividualDto)
	implicit val updateDtoFormat = jsonFormat4(UpdateDto)
	implicit val replaceDtoFormat = jsonFormat4(ReplaceDto)
}
