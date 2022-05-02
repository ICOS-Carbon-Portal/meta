package se.lu.nateko.cp.meta.api

import spray.json.*
import java.net.URL
import DefaultJsonProtocol.*

sealed trait HandleData

case class StringHandleData(value: String) extends HandleData

case class AdminValue(handle: String, index: Int, permissions: String)

case class AdminHandleData(value: AdminValue) extends HandleData

case class AnyHandleData(format: String, value: JsValue) extends HandleData

object HandleData{

	val StringFormat = "string"
	val AdminFormat = "admin"

	given RootJsonFormat[AdminValue] = jsonFormat3(AdminValue.apply)
	given RootJsonFormat[AnyHandleData] = jsonFormat2(AnyHandleData.apply)

	def toAny(hd: HandleData): AnyHandleData = hd match {
		case any: AnyHandleData => any
		case StringHandleData(value) => AnyHandleData(StringFormat, JsString(value))
		case AdminHandleData(value) => AnyHandleData(AdminFormat, value.toJson)
	}

	given JsonFormat[HandleData] with {

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

object HandleValue{
	val UrlType = "URL"
	val HsAdminType = "HS_ADMIN"

	import HandleData.given
	given RootJsonFormat[AnyHandleValue] = jsonFormat3(AnyHandleValue.apply)

	def toAny(hv: HandleValue): AnyHandleValue = hv match{
		case any: AnyHandleValue => any
		case UrlHandleValue(index, url) => AnyHandleValue(index, UrlType, StringHandleData(url.toString))
		case AdminHandleValue(index, admin) => AnyHandleValue(index, HsAdminType, AdminHandleData(admin))
	}

	given JsonFormat[HandleValue] with {

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

object HandleValues{
	import HandleValue.given
	given RootJsonFormat[HandleValues] =  jsonFormat1(HandleValues.apply)
}

case class HandleList(handles: Seq[String])

object HandleList{
	given RootJsonFormat[HandleList] = jsonFormat1(HandleList.apply)
}
