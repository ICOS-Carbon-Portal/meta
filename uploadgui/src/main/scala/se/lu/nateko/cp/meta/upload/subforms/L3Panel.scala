package se.lu.nateko.cp.meta.upload.subforms


import scala.util.{Try, Success, Failure}

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.SubmitterProfile
import se.lu.nateko.cp.meta.upload._
import se.lu.nateko.cp.meta.{UploadDto, DataObjectDto, DataProductionDto, ElaboratedProductMetadata}

import formcomponents._
import ItemTypeRadio.{ItemType, Collection, Data, Document}
import UploadApp.whenDone
import Utils._

class L3Panel(implicit bus: PubSubBus, envri: Envri.Envri) extends PanelSubform(".l3-section"){

	def meta(productionDto: => Try[DataProductionDto]): Try[ElaboratedProductMetadata] = ???

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
		Iterable(
			titleInput, descriptionInput, timeStartInput, timeStopInput,
			temporalResInput, externalPageInput, minLatInput, minLonInput,
			maxLatInput, maxLonInput
		).foreach(_.reset())
	}

	bus.subscribe{
		case GotUploadDto(upDto) => handleDto(upDto)
		case ObjSpecSelected(spec) => onLevelSelected(spec.dataLevel)
		case LevelSelected(level) => onLevelSelected(level)
	}

	private def onLevelSelected(level: Int): Unit = if(level == 3) show() else hide()

	private def handleDto(upDto: UploadDto): Unit = upDto match {
		case dto: DataObjectDto => dto.specificInfo match{
			case Left(l3) =>
				titleInput.value = l3.title
				descriptionInput.value = l3.description
				timeStartInput.value = l3.temporal.interval.start
				timeStopInput.value = l3.temporal.interval.stop
				temporalResInput.value = l3.temporal.resolution
				externalPageInput.value = l3.customLandingPage
				val box = l3.spatial.left.toOption
				minLatInput.value = box.map(_.min.lat)
				minLonInput.value = box.map(_.min.lon)
				maxLatInput.value = box.map(_.max.lat)
				maxLonInput.value = box.map(_.max.lon)
				show()
			case _ =>
				hide()
		}
		case _ =>
			hide()
	}
}
