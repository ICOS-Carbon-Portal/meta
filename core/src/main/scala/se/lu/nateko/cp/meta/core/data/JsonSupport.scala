package se.lu.nateko.cp.meta.core.data

import se.lu.nateko.cp.meta.core.CommonJsonSupport
import spray.json._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.time.Instant

object JsonSupport extends CommonJsonSupport{

	import Sha256Sum.sha256sumFormat

	implicit val uriResourceFormat = jsonFormat2(UriResource)
	implicit val dataPackageSpecFormat = jsonFormat3(DataObjectSpec)
	implicit val packageProductionFormat = jsonFormat5(DataProduction)

	implicit val packageSubmissionFormat = jsonFormat3(DataSubmission)

	implicit object dataObjectStatusFormat extends RootJsonFormat[DataObjectStatus] {

		def write(status: DataObjectStatus) = JsString(status match{
			case UploadOk => "OK"
			case NotComplete => "INCOMPLETE"
		})

		def read(value: JsValue): DataObjectStatus = value match{
			case JsString("OK") => UploadOk
			case JsString("INCOMPLETE") => NotComplete
			case _ => deserializationError("Expected 'OK' or 'INCOMPLETE'")
		}
	}

	implicit val dataPackageFormat = jsonFormat8(DataObject)

}