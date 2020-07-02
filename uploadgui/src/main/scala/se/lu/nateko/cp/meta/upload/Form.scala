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
	val formElement = new FormElement("form-block")

	def resetForm() = {
		formElement.reset()
		updateButton()
	}

	val dataElements = new HtmlElements(".data-section")
	val collectionElements = new HtmlElements(".collection-section")
	val productionElements = new HtmlElements(".production-section")
	val acquisitionSection = new HtmlElements(".acq-section")
	val l3Section = new HtmlElements(".l3-section")

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
						for(dto <- dataObjectDto; file <- aboutPanel.file; nRows <- dataPanel.nRows; spec <- dataPanel.objSpec) {
							whenDone(Backend.tryIngestion(file, spec, nRows)){ _ =>
								onUpload(dto, Some(file))
							}.failed.foreach {
								case _ => progressBar.hide()
							}
						}
					}
				} else{
					for(dto <- dataObjectDto) {
						onUpload(dto, None)
					}
				}
			case Some(Collection) =>
				for(dto <- staticCollectionDto) {
					onUpload(dto, None)
				}
			case Some(Document) =>
				if(aboutPanel.isInNewItemMode) {
					for(dto <- documentObjectDto; file <- aboutPanel.file) {
						onUpload(dto, Some(file))
					}
				} else {
					for(dto <- documentObjectDto) {
						onUpload(dto, None)
					}
				}
			case _ =>
		}
	}
	val submitButton = new Button("submitbutton", () => submitAction())

	private def updateButton(): Unit = dto match {
		case Success(_) => submitButton.enable()
		case Failure(err) => submitButton.disable(err.getMessage)
	}


	val addProductionButton = new Button("addproductionbutton", () => {
		disableProductionButton()
		productionElements.show()
		updateButton()
	})

	val disableProductionButton: () => Unit = () => {
		addProductionButton.disable("")
	}

	val enableProductionButton: () => Unit = () => {
		addProductionButton.enable()
	}

	val removeProductionButton = new Button("removeproductionbutton", () => {
		enableProductionButton()
		productionElements.hide()
		updateButton()
	})

	val creatorInput = new UriInput("creatoruri", updateButton)
	val contributorsInput = new UriListInput("contributors", updateButton)
	val hostOrganizationInput = new UriOptInput("hostorganisation", updateButton)
	val commentInput = new TextOptInput("productioncomment", updateButton)
	val creationDateInput = new InstantInput("creationdate", updateButton)
	val sourcesInput = new HashOptListInput("sources", updateButton)

	val collectionTitle = new TextInput("collectiontitle", updateButton)
	val collectionDescription = new TextOptInput("collectiondescription", updateButton)
	val collectionMembers = new NonEmptyUriListInput("collectionmembers", updateButton)


	def dto: Try[UploadDto] = aboutPanel.itemType match {
		case Some(Data) => dataObjectDto
		case Some(Collection) => staticCollectionDto
		case Some(Document) => documentObjectDto
		case _ => fail("No file type selected")
	}

	def dataProductionDto: Try[Option[DataProductionDto]] = for(
		creator <- creatorInput.value.withErrorContext("Creator");
		contributors <- contributorsInput.value.withErrorContext("Contributors");
		hostOrganization <- hostOrganizationInput.value.withErrorContext("Host organization");
		comment <- commentInput.value.withErrorContext("Comment");
		creationDate <- creationDateInput.value.withErrorContext("Creation date");
		sources <- sourcesInput.value.withErrorContext("Sources")
	) yield Some(DataProductionDto(
		creator = creator,
		contributors = contributors,
		hostOrganization = hostOrganization,
		comment = comment,
		sources = sources,
		creationDate = creationDate
	))

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
		production <- if(productionElements.areEnabled) dataProductionDto else Success(None)
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
	def documentObjectDto: Try[DocObjectDto] = for(
		submitter <- aboutPanel.submitter;
		file <- aboutPanel.itemName;
		hash <- aboutPanel.itemHash;
		previousVersion <- aboutPanel.previousVersion;
		doi <- aboutPanel.existingDoi
	) yield DocObjectDto(
		hashSum = hash,
		submitterId = submitter.id,
		fileName = file,
		isNextVersionOf = previousVersion,
		preExistingDoi = doi
	)
	def staticCollectionDto: Try[StaticCollectionDto] = for(
		title <- collectionTitle.value.withErrorContext("Collection title");
		description <- collectionDescription.value;
		members <- collectionMembers.value.withErrorContext("Collection members (list of object urls)");
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
		resetForm()
		upDto match {
			case dto: DataObjectDto => {
				dto.specificInfo match {
					case Left(_) =>
					case Right(acquisition) => {
						acquisition.production.map { production =>
							disableProductionButton()
							productionElements.show()
							creatorInput.value = production.creator
							contributorsInput.value = production.contributors
							hostOrganizationInput.value = production.hostOrganization
							commentInput.value = production.comment
							creationDateInput.value = production.creationDate
							sourcesInput.value = production.sources
						}
					}
				}
			}
			case dto: DocObjectDto =>
				updateButton()
			case dto: StaticCollectionDto =>
				collectionTitle.value = dto.title
				collectionMembers.value = dto.members
				collectionDescription.value = dto.description
				updateButton()
			case _ =>
		}
	}
}
