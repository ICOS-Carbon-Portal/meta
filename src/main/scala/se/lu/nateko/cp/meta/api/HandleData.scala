package se.lu.nateko.cp.meta.api

import spray.json._
import java.net.URL

sealed trait HandleData

case class StringHandleData(value: String) extends HandleData

case class AdminValue(handle: String, index: Int, permissions: String)

case class AdminHandleData(value: AdminValue) extends HandleData

case class AnyHandleData(format: String, value: JsValue) extends HandleData

object HandleData extends DefaultJsonProtocol{

	val StringFormat = "string"
	val AdminFormat = "admin"

	implicit val adminValueFormat = jsonFormat3(AdminValue)
	implicit val anyHandleDataFormat = jsonFormat2(AnyHandleData)

	def toAny(hd: HandleData): AnyHandleData = hd match {
		case any: AnyHandleData => any
		case StringHandleData(value) => AnyHandleData(StringFormat, JsString(value))
		case AdminHandleData(value) => AnyHandleData(AdminFormat, value.toJson)
	}

	implicit object handleDataFormat extends JsonFormat[HandleData] {

		def read(json: JsValue): HandleData = {
			val data = json.convertTo[AnyHandleData]
			data.format match{
				case StringFormat => StringHandleData(
					data.value match {
						case JsString(value) => value
						case _ => deserializationError("Expected a JSON string", fieldNames = "value" :: Nil)
					}
				)
				case AdminFormat =>
					AdminHandleData(data.value.convertTo[AdminValue])
				case _ =>
					data
			}
		}

		def write(hd: HandleData): JsValue = toAny(hd).toJson
	}

}

sealed trait HandleValue{
	def index: Int
}

case class UrlHandleValue(index: Int, url: URL) extends HandleValue
case class AdminHandleValue(index: Int, admin: AdminValue) extends HandleValue
case class AnyHandleValue(index: Int, `type`: String, data: HandleData) extends HandleValue

object HandleValue extends DefaultJsonProtocol{
	val UrlType = "URL"
	val HsAdminType = "HS_ADMIN"

	import HandleData.handleDataFormat
	implicit val anyHandleValueFormat = jsonFormat3(AnyHandleValue)

	def toAny(hv: HandleValue): AnyHandleValue = hv match{
		case any: AnyHandleValue => any
		case UrlHandleValue(index, url) => AnyHandleValue(index, UrlType, StringHandleData(url.toString))
		case AdminHandleValue(index, admin) => AnyHandleValue(index, HsAdminType, AdminHandleData(admin))
	}

	implicit object handleValueFormat extends JsonFormat[HandleValue] {

		def read(json: JsValue): HandleValue = {
			val v = json.convertTo[AnyHandleValue]
			v.data match {
				case StringHandleData(str) if v.`type` == UrlType =>
					UrlHandleValue(v.index, new URL(str))
				case AdminHandleData(admin) if v.`type` == HsAdminType =>
					AdminHandleValue(v.index, admin)
				case _ =>
					v
			}
		}

		def write(hv: HandleValue): JsValue = toAny(hv).toJson
	}
}

case class HandleValues(values: Seq[HandleValue])

object HandleValues extends DefaultJsonProtocol{
	import HandleValue.handleValueFormat
	implicit val handleValuesFormat = jsonFormat1(HandleValues.apply)
}

case class HandleList(handles: Seq[String])

object HandleList extends DefaultJsonProtocol{
	implicit val handleListFormat = jsonFormat1(HandleList.apply)
}
