package se.lu.nateko.cp.meta.core.etcupload

import se.lu.nateko.cp.meta.core.CommonJsonSupport
import se.lu.nateko.cp.meta.core.crypto.JsonSupport.given
import spray.json.*
import DefaultJsonProtocol.*

object JsonSupport extends CommonJsonSupport{

	given RootJsonFormat[StationId] with {

		def write(id: StationId) = JsString(id.id)

		def read(value: JsValue): StationId = value match{
			case JsString(StationId(id)) => id
			case _ => deserializationError("Expected string of the format CC-Xxx")
		}
	}

	given JsonFormat[DataType] = enumFormat(DataType.valueOf, DataType.values)
	given RootJsonFormat[EtcUploadMetadata] = jsonFormat8(EtcUploadMetadata.apply)
}
