package se.lu.nateko.cp.meta.upload.subforms

import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

import org.scalajs.dom
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import se.lu.nateko.cp.doi.Doi

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.SubmitterProfile
import se.lu.nateko.cp.meta.upload._
import se.lu.nateko.cp.meta.{UploadDto, DataObjectDto, DocObjectDto, StaticCollectionDto}

import formcomponents._
import ItemTypeRadio.{ItemType, Collection, Data, Document}
import UploadApp.whenDone
import Utils._


class DataPanel(objSpecs: IndexedSeq[ObjSpec])(implicit bus: PubSubBus, envri: Envri.Envri) {
	def nRows: Try[Option[Int]] = nRowsInput.value.withErrorContext("Number of rows")
	def objSpec: Try[ObjSpec] = objSpecSelect.value.withMissingError("Data type not set")
	def keywords: Try[String] = keywordsInput.value

	private val htmlElements = new HtmlElements(".data-section")
	private val levelControl = new Radio[Int]("level-radio", onLevelSelected, s => Try(s.toInt).toOption, _.toString)
	private val objSpecSelect = new Select[ObjSpec]("objspecselect", _.name, cb = onSpecSelected)
	private val nRowsInput = new IntOptInput("nrows", notifyUpdate)
	private val keywordsInput = new TextInput("keywords", () => ())

	def resetForm(): Unit = {
		levelControl.value = Int.MinValue
		objSpecSelect.setOptions(IndexedSeq.empty)
		nRowsInput.value = None
		keywordsInput.value = ""
	}

	bus.subscribe{
		case GotUploadDto(dto) => handleDto(dto)
		case ItemTypeSelected(Data) =>
			resetForm()
			htmlElements.show()
		case ItemTypeSelected(_) => htmlElements.hide()
	}

	private def onLevelSelected(level: Int): Unit = {
		objSpecSelect.setOptions(objSpecs.filter(_.dataLevel == level))
	}

	private def onSpecSelected(): Unit = {
		objSpecSelect.value.foreach{ objSpec =>
			if(objSpec.hasDataset && objSpec.dataLevel <= 2) nRowsInput.enable() else nRowsInput.disable()
			bus.publish(ObjSpecSelected(objSpec))
		}
		notifyUpdate()
	}

	private def notifyUpdate(): Unit = bus.publish(FormInputUpdated)

	private def handleDto(upDto: UploadDto): Unit = upDto match {
		case dto: DataObjectDto =>
			objSpecs.find(_.uri == dto.objectSpecification).foreach{spec =>
				levelControl.value = spec.dataLevel
				onLevelSelected(spec.dataLevel)
				objSpecSelect.value = spec
				onSpecSelected()
			}
			keywordsInput.value = dto.references.fold("")(_.keywords.fold("")(_.mkString(", ")))
			dto.specificInfo match {
				case Right(l2) => nRowsInput.value = l2.nRows
				case _ =>
			}

			htmlElements.show()
		case _ =>
			htmlElements.hide()
	}
}
