package se.lu.nateko.cp.meta.core.data

import se.lu.nateko.cp.meta.core.CommonJsonSupport
import spray.json._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.time.Instant

object JsonSupport extends CommonJsonSupport{

	import Sha256Sum.sha256sumFormat

	implicit val uriResourceFormat = jsonFormat2(UriResource)
	implicit val dataPackageSpecFormat = jsonFormat3(DataPackageSpec)
	implicit val packageProductionFormat = jsonFormat1(PackageProduction)

	implicit object javaDateFormat extends RootJsonFormat[Instant] {

		def write(instant: Instant) = JsString(instant.toString)

		def read(value: JsValue): Instant = value match{
			case JsString(s) => Instant.parse(s)
			case _ => deserializationError("String representation of a time instant is expected")
		}
	}

	implicit val packageSubmissionFormat = jsonFormat3(PackageSubmission)
	implicit val dataPackageFormat = jsonFormat4(DataPackage)

}