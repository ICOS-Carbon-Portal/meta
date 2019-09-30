package se.lu.nateko.cp.meta.upload

import org.scalajs.dom
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.{StationDataMetadata, SubmitterProfile, DataObjectDto, DocObjectDto, UploadDto, StaticCollectionDto, DataProductionDto}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import Utils._
import FormTypeRadio._

class Form(
	onUpload: (UploadDto, Option[dom.File]) => Unit,
	onSubmitterSelect: SubmitterProfile => Future[IndexedSeq[Station]]
)(implicit envri: Envri.Envri, envriConf: EnvriConfig) {

	val dataElements = new HtmlElements(".data-section")
	val collectionElements = new HtmlElements(".collection-section")
	val addProductionElements = new HtmlElements(".add-production-section")
	val productionElements = new HtmlElements(".production-section")

	val onFormTypeSelected: FormType => Unit = formType => {
		dataElements.hide()
		collectionElements.hide()
		fileInput.enable()
		productionElements.hide()

		formType match {
			case Data => dataElements.show()
			case Collection => {
				collectionElements.show()
				fileInput.disable()
			}
			case Document =>
		}
		updateButton()
	}

	def submitAction(): Unit = {
		dom.window.scrollTo(0, 0)
		submitButton.disable("")
		typeControl.formType match {
			case Data =>
				for(dto <- dataObjectDto; file <- fileInput.file; nRows <- nRowsInput.value; spec <- objSpecSelect.value) {
					whenDone(Backend.tryIngestion(file, spec, nRows)){ _ =>
						onUpload(dto, Some(file))
					}
				}
			case Collection =>
				for(dto <- staticCollectionDto) {
					onUpload(dto, None)
				}
			case Document =>
				for(dto <- documentObjectDto; file <- fileInput.file) {
					onUpload(dto, Some(file))
				}
		}
	}
	val submitButton = new Button("submitbutton", () => submitAction())
	val updateButton: () => Unit = () => dto match {
		case Success(_) => submitButton.enable()
		case Failure(err) => submitButton.disable(err.getMessage)
	}

	val addProductionButton = new Button("addproductionbutton", () => {
		addProductionElements.hide()
		productionElements.show()
		updateButton()
	})

	val removeProductionButton = new Button("removeproductionbutton", () => {
		addProductionElements.show()
		productionElements.hide()
		updateButton()
	})

	val onLevelSelected: String => Unit = (level: String) =>
		whenDone{
			Backend.getObjSpecs.map(_.filter(_.dataLevel == level.toInt))
		}(objSpecSelect.setOptions)

	val onSubmitterSelected: () => Unit = () =>
		submitterIdSelect.value.foreach{submitter =>
			whenDone(onSubmitterSelect(submitter)){stations =>
				stationSelect.setOptions(stations)
				updateButton()
			}
		}

	private val onSpecSelected: () => Unit = () => {
		objSpecSelect.value.foreach{ objSpec =>
			if(objSpec.hasDataset){
				nRowsInput.enable()
				acqStartInput.disable()
				acqStopInput.disable()
			} else{
				nRowsInput.disable()
				acqStartInput.enable()
				acqStopInput.enable()
			}
		}
		updateButton()
	}

	private val onStationSelected: () => Unit = () => {
		stationSelect.value.foreach { station =>
			whenDone(Backend.getSites(station.uri)) { sites =>
				siteSelect.setOptions {
					if (sites.isEmpty) IndexedSeq.empty
					else None +: sites.map(Some(_))
				}
			}
			updateButton()
		}
	}

	val fileInput = new FileInput("fileinput", updateButton)
	val typeControl = new FormTypeRadio("file-type-radio", onFormTypeSelected)

	val previousVersionInput = new HashOptInput("previoushash", updateButton)
	val existingDoiInput = new DoiOptInput("existingdoi", updateButton)
	val levelControl = new Radio("level-radio", onLevelSelected)
	val stationSelect = new Select[Station]("stationselect", s => s"${s.id} (${s.name})", cb = onStationSelected)
	val siteSelect = new Select[Option[Site]]("siteselect", _.map(_.name).getOrElse(""), cb = updateButton)
	val objSpecSelect = new Select[ObjSpec]("objspecselect", _.name, cb = onSpecSelected)
	val nRowsInput = new IntOptInput("nrows", updateButton)

	val submitterIdSelect = new Select[SubmitterProfile]("submitteridselect", _.id, autoselect = true, onSubmitterSelected)

	val acqStartInput = new InstantInput("acqstartinput", updateButton)
	val acqStopInput = new InstantInput("acqstopinput", updateButton)
	val samplingHeightInput = new FloatOptInput("sampleheight", updateButton)
	val instrUriInput = new UriOptInput("instrumenturi", updateButton)
	val timeIntevalInput = new TimeIntevalInput(acqStartInput, acqStopInput)

	val creatorInput = new UriInput("creatoruri", updateButton)
	val contributorsInput = new UriListInput("contributors", updateButton)
	val hostOrganizationInput = new UriOptInput("hostorganisation", updateButton)
	val commentInput = new TextOptInput("productioncomment", updateButton)
	val creationDateInput = new InstantInput("creationdate", updateButton)
	val sourcesInput = new HashOptListInput("sources", updateButton)

	val collectionTitle = new TextInput("collectiontitle", updateButton)
	val collectionDescription = new TextOptInput("collectiondescription", updateButton)
	val collectionMembers = new NonEmptyUriListInput("collectionmembers", updateButton)

	def dto: Try[UploadDto] = typeControl.formType match {
		case Data => dataObjectDto
		case Collection => staticCollectionDto
		case Document => documentObjectDto
	}
	private def isTypeSelected = if (typeControl.value.isEmpty) fail("No file type selected") else Success(())

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
		submitter <- submitterIdSelect.value.withMissingError("Submitter Id not set");
		file <- fileInput.file;
		hash <- fileInput.hash;
		_ <- isTypeSelected;
		previousVersion <- previousVersionInput.value.withErrorContext("Previous version");
		doi <- existingDoiInput.value.withErrorContext("Pre-existing DOI");
		station <- stationSelect.value.withMissingError("Station not chosen");
		objSpec <- objSpecSelect.value.withMissingError("Data type not set");
		acqInterval <- timeIntevalInput.value.withErrorContext("Acqusition time interval");
		nRows <- nRowsInput.value.withErrorContext("Number of rows");
		samplingHeight <- samplingHeightInput.value.withErrorContext("Sampling height");
		instrumentUri <- instrUriInput.value.withErrorContext("Instrument URI");
		production <- if(productionElements.areEnabled) dataProductionDto else Success(None)
	) yield DataObjectDto(
		hashSum = hash,
		submitterId = submitter.id,
		objectSpecification = objSpec.uri,
		fileName = file.name,
		specificInfo = Right(
			StationDataMetadata(
				station = station.uri,
				site = siteSelect.value.flatten.map(_.uri),
				instrument = instrumentUri.map(Left(_)),
				samplingHeight = samplingHeight,
				acquisitionInterval = acqInterval,
				nRows = nRows,
				production = production
			)
		),
		isNextVersionOf = previousVersion.map(Left(_)),
		preExistingDoi = doi
	)
	def documentObjectDto: Try[DocObjectDto] = for(
		file <- fileInput.file;
		hash <- fileInput.hash;
		_ <- isTypeSelected;
		previousVersion <- previousVersionInput.value.withErrorContext("Previous version");
		doi <- existingDoiInput.value.withErrorContext("Pre-existing DOI");
		submitter <- submitterIdSelect.value.withMissingError("Submitter Id not set")
	) yield DocObjectDto(
		hashSum = hash,
		submitterId = submitter.id,
		fileName = file.name,
		isNextVersionOf = previousVersion.map(Left(_)),
		preExistingDoi = doi
	)
	def staticCollectionDto: Try[StaticCollectionDto] = for(
		title <- collectionTitle.value.withErrorContext("Collection title");
		description <- collectionDescription.value;
		members <- collectionMembers.value.withErrorContext("Collection members (list of object urls)");
		_ <- isTypeSelected;
		previousVersion <- previousVersionInput.value.withErrorContext("Previous version");
		doi <- existingDoiInput.value.withErrorContext("Pre-existing DOI");
		submitter <- submitterIdSelect.value.withMissingError("Submitter Id not set")
	) yield StaticCollectionDto(
		submitterId = submitter.id,
		members = members,
		title = title,
		description = description,
		isNextVersionOf = previousVersion.map(Left(_)),
		preExistingDoi = doi
	)
}
