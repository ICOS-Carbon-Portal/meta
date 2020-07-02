package se.lu.nateko.cp.meta.upload

import org.scalajs.dom
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.{StationDataMetadata, SubmitterProfile, DataObjectDto, DocObjectDto, UploadDto, StaticCollectionDto, DataProductionDto}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import Utils._
import se.lu.nateko.cp.meta.upload.formcomponents._
import ItemTypeRadio._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.net.URI
import UploadApp.{hideAlert, showAlert, whenDone, progressBar}
import se.lu.nateko.cp.meta.upload.subforms._

class Form(
	subms: IndexedSeq[SubmitterProfile],
	objSpecs: IndexedSeq[ObjSpec],
	onUpload: (UploadDto, Option[dom.File]) => Unit,
)(implicit envri: Envri.Envri, envriConf: EnvriConfig, bus: PubSubBus) {

	val aboutPanel = new AboutPanel(subms)
	val dataPanel = new DataPanel(objSpecs)
	val acqPanel = new AcquisitionPanel
	val prodPanel = new ProductionPanel
	val collPanel = new CollectionPanel
	val l3Panel = new L3Panel

	val productionElements = new HtmlElements(".production-section")

	bus.subscribe{
		case GotUploadDto(dto) => handleDto(dto)
		case FormInputUpdated => updateButton()
	}

	def submitAction(): Unit = {
		dom.window.scrollTo(0, 0)
		submitButton.disable("")
		hideAlert()
		progressBar.show()
		aboutPanel.itemType match {
			case Some(Data) =>
				if(aboutPanel.isInNewItemMode) {
					whenDone {
						aboutPanel.refreshFileHash()
					}{ _ =>
						for(
							dto <- dataObjectDto;
							file <- aboutPanel.file;
							nRows <- dataPanel.nRows;
							spec <- dataPanel.objSpec
						) {
							whenDone(Backend.tryIngestion(file, spec, nRows)){ _ =>
								onUpload(dto, Some(file))
							}.failed.foreach {
								case _ => progressBar.hide()
							}
						}
					}
				} else
					dataObjectDto.foreach(onUpload(_, None))

			case Some(Collection) =>
				staticCollectionDto.foreach(onUpload(_, None))

			case Some(Document) =>
				for(
					dto <- aboutPanel.documentObjectDto;
					fileOpt <- if(aboutPanel.isInNewItemMode) aboutPanel.file.map(Some.apply) else Success(None)
				) onUpload(dto, fileOpt)

			case _ =>
		}
	}
	val submitButton = new Button("submitbutton", () => submitAction())

	private def updateButton(): Unit = dto match {
		case Success(_) => submitButton.enable()
		case Failure(err) => submitButton.disable(err.getMessage)
	}


	val addProductionButton: Button = new Button("addproductionbutton", () => {
		addProductionButton.disable("")
		productionElements.show()
		updateButton()
	})

	val removeProductionButton = new Button("removeproductionbutton", () => {
		addProductionButton.enable()
		productionElements.hide()
		updateButton()
	})

	def dto: Try[UploadDto] = aboutPanel.itemType match {
		case Some(Data) => dataObjectDto
		case Some(Collection) => staticCollectionDto
		case Some(Document) => aboutPanel.documentObjectDto
		case _ => fail("No file type selected")
	}

	def dataObjectDto: Try[DataObjectDto] = for(
		submitter <- aboutPanel.submitter;
		file <- aboutPanel.itemName;
		hash <- aboutPanel.itemHash;
		previousVersion <- aboutPanel.previousVersion;
		doi <- aboutPanel.existingDoi;
		station <- acqPanel.station;
		objSpec <- dataPanel.objSpec;
		acqInterval <- acqPanel.timeInterval;
		nRows <- dataPanel.nRows;
		samplingPoint <- acqPanel.samplingPoint;
		samplingHeight <- acqPanel.samplingHeight;
		instrumentUri <- acqPanel.instrUri;
		production <- prodPanel.dataProductionDto
	) yield DataObjectDto(
		hashSum = hash,
		submitterId = submitter.id,
		objectSpecification = objSpec.uri,
		fileName = file,
		specificInfo = Right(
			StationDataMetadata(
				station = station.uri,
				site = acqPanel.site.flatten.map(_.uri),
				instrument = instrumentUri,
				samplingPoint = samplingPoint,
				samplingHeight = samplingHeight,
				acquisitionInterval = acqInterval,
				nRows = nRows,
				production = production
			)
		),
		isNextVersionOf = previousVersion,
		preExistingDoi = doi,
		references = Some(
			References(
				citationString = None,
				keywords = dataPanel.keywords.toOption.map(_.split(",").toIndexedSeq.map(_.trim).filter(!_.isEmpty)).filter(!_.isEmpty)
			)
		)
	)

	def staticCollectionDto: Try[StaticCollectionDto] = for(
		title <- collPanel.title;
		description <- collPanel.description;
		members <- collPanel.members;
		previousVersion <- aboutPanel.previousVersion;
		doi <- aboutPanel.existingDoi;
		submitter <- aboutPanel.submitter
	) yield StaticCollectionDto(
		submitterId = submitter.id,
		members = members,
		title = title,
		description = description,
		isNextVersionOf = previousVersion,
		preExistingDoi = doi
	)

	private def handleDto(upDto: UploadDto): Unit = {
		hideAlert()
		upDto match {
			case dto: DataObjectDto => {
				val hasProduction = dto.specificInfo.fold(_ => true, _.production.isDefined)
				if(hasProduction) addProductionButton.disable("")
			}
			case _ =>
		}
	}
}
