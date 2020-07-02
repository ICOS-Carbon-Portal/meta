package se.lu.nateko.cp.meta.upload.subforms


import scala.util.{Try, Success, Failure}

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.SubmitterProfile
import se.lu.nateko.cp.meta.upload._
import se.lu.nateko.cp.meta.{UploadDto, DataObjectDto, ElaboratedProductMetadata}

import formcomponents._
import ItemTypeRadio.{ItemType, Collection, Data, Document}
import UploadApp.whenDone
import Utils._

class L3Panel(implicit bus: PubSubBus, envri: Envri.Envri) {

	def meta: Try[ElaboratedProductMetadata] = ???

	private val htmlElements = new HtmlElements(".l3-section")
	private val spatCoverElements = new HtmlElements(".l3spatcover-element")

	private val titleInput = new TextInput("l3title", notifyUpdate)
	private val descriptionInput = new TextOptInput("l3descr", notifyUpdate)
	private val timeStartInput = new InstantInput("l3startinput", notifyUpdate)
	private val timeStopInput = new InstantInput("l3stopinput", notifyUpdate)
	private val timeIntevalInput = new TimeIntevalInput(timeStartInput, timeStopInput)
	private val temporalResInput = new TextOptInput("l3tempres", notifyUpdate)
	private val externalPageInput = new UriOptInput("l3landingpage", notifyUpdate)

	private val minLatInput = new DoubleOptInput("l3minlat", notifyUpdate)
	private val minLonInput = new DoubleOptInput("l3minlon", notifyUpdate)
	private val maxLatInput = new DoubleOptInput("l3maxlat", notifyUpdate)
	private val maxLonInput = new DoubleOptInput("l3maxlon", notifyUpdate)

	def resetForm(): Unit = {
		???
	}

	bus.subscribe{
		case GotUploadDto(upDto) => handleDto(upDto)
		case ItemTypeSelected(Data) => resetForm()
		case ItemTypeSelected(_) => htmlElements.hide()
		case ObjSpecSelected(spec) =>
			if(spec.dataLevel == 3) htmlElements.show() else htmlElements.hide()
	}

	private def notifyUpdate(): Unit = bus.publish(FormInputUpdated)

	private def handleDto(upDto: UploadDto): Unit = upDto match {
		case dto: DataObjectDto => dto.specificInfo match{
			case Left(l3) =>
				???
				htmlElements.show()
			case _ =>
				htmlElements.hide()
		}
		case _ =>
			htmlElements.hide()
	}
}
