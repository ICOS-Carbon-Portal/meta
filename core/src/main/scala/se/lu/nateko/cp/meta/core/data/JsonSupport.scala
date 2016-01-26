package se.lu.nateko.cp.meta.core.data

import se.lu.nateko.cp.meta.core.CommonJsonSupport
import spray.json._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.time.Instant

object JsonSupport extends CommonJsonSupport{

	import Sha256Sum.sha256sumFormat

	implicit val uriResourceFormat = jsonFormat2(UriResource)
	implicit val dataPackageSpecFormat = jsonFormat3(DataObjectSpec)
	implicit val packageProductionFormat = jsonFormat3(DataProduction)

	implicit val packageSubmissionFormat = jsonFormat3(DataSubmission)
	implicit val dataPackageFormat = jsonFormat7(DataObject)

}