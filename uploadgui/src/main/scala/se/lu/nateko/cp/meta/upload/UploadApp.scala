package se.lu.nateko.cp.meta.upload

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object UploadApp {
	import Utils._

	val form = new Form(upload)

	def main(args: Array[String]): Unit = {

		whenDone(Backend.sitesStationInfo)(form.stationSelect.setOptions)

		whenDone{
			Backend.getSitesObjSpecs.map(_.filter(_.dataLevel == 0))
		}(form.objSpecSelect.setOptions)

		whenDone(Backend.submitterIds)(form.submitterIdSelect.setOptions)
	}

	def upload(): Unit = form.dto.foreach{dto =>
		whenDone(Backend.submitMetadata(dto))(uri => {
			println(s"Metadata uploaded, upload data to $uri")
		})
	}
}
