package se.lu.nateko.cp.meta.upload.subforms

import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

import org.scalajs.dom
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import se.lu.nateko.cp.doi.Doi

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.SubmitterProfile
import se.lu.nateko.cp.meta.upload._
import se.lu.nateko.cp.meta.{UploadDto, DataObjectDto, DocObjectDto, StaticCollectionDto}

import formcomponents._
import ItemTypeRadio.{ItemType, Collection, Data, Document}
import UploadApp.whenDone
import Utils._


class AcquisitionPanel(implicit bus: PubSubBus, envri: Envri.Envri) {
	def timeInterval: Try[Option[TimeInterval]] = timeIntevalInput.value.withErrorContext("Acqusition time interval")

	private val acqStartInput = new InstantInput("acqstartinput", updateForm)
	private val acqStopInput = new InstantInput("acqstopinput", updateForm)
	private val timeIntevalInput = new TimeIntevalInput(acqStartInput, acqStopInput)

	bus.subscribe{
		case ObjSpecSelected(objSpec) =>
			if (objSpec.hasDataset && objSpec.dataLevel <= 2) {
				acqStartInput.disable()
				acqStopInput.disable()
			} else {
				acqStartInput.enable()
				acqStopInput.enable()
			}
	}

	private def updateForm(): Unit = bus.publish(FormInputUpdated)

}
