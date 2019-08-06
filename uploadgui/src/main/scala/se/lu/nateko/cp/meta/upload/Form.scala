package se.lu.nateko.cp.meta.upload

import org.scalajs.dom
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.{StationDataMetadata, SubmitterProfile, DataObjectDto, DocObjectDto, UploadDto, StaticCollectionDto}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import Utils._

class Form(
	onUpload: (UploadDto, Option[dom.File]) => Unit,
	onSubmitterSelect: SubmitterProfile => Future[IndexedSeq[Station]]
)(implicit envri: Envri.Envri, envriConf: EnvriConfig) {

	var formType: Option[String] = None
	val dataElements = new HtmlElements(".data-section")
	val collectionElements = new HtmlElements(".collection-section")
	val onFileTypeSelected: String => Unit = (fileType: String) => {
		fileType match {
			case "data" => {
				dataElements.show()
				collectionElements.hide()
				fileInput.enable()
			}
			case "document" => {
				dataElements.hide()
				collectionElements.hide()
				fileInput.enable()
			}
			case "collection" => {
				dataElements.hide()
				collectionElements.show()
				fileInput.disable()
			}
		}
		updateButton()
	}

	def submitAction(): Unit = typeControl.value match {
		case Some("data") =>
			for(dto <- dto; file <- fileInput.file; nRows <- nRowsInput.value; spec <- objSpecSelect.value) {
				whenDone(Backend.tryIngestion(file, spec, nRows)){ _ =>
					onUpload(dto, Some(file))
				}
			}
		case Some("document") =>
			for(dto <- dto; file <- fileInput.file) {
				onUpload(dto, Some(file))
			}
		case Some("collection") =>
			for(dto <- dto) {
				onUpload(dto, None)
			}
		case _ => ()
	}
	val button = new SubmitButton("submitbutton", () => submitAction())
	val updateButton: () => Unit = () => dto match {
		case Success(_) => button.enable()
		case Failure(err) => button.disable(err.getMessage)
	}

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

	val fileInput = new FileInput("fileinput", updateButton)
	val typeControl = new Radio("file-type-radio", onFileTypeSelected)

	val previousVersionInput = new HashOptInput("previoushash", updateButton)
	val existingDoiInput = new DoiOptInput("existingdoi", updateButton)
	val levelControl = new Radio("level-radio", onLevelSelected)
	val stationSelect = new Select[Station]("stationselect", s => s"${s.id} (${s.name})", updateButton)
	val objSpecSelect = new Select[ObjSpec]("objspecselect", _.name, onSpecSelected)
	val nRowsInput = new IntOptInput("nrows", updateButton)

	val submitterIdSelect = new Select[SubmitterProfile]("submitteridselect", _.id, onSubmitterSelected)

	val acqStartInput = new InstantInput("acqstartinput", updateButton)
	val acqStopInput = new InstantInput("acqstopinput", updateButton)
	val samplingHeightInput = new FloatOptInput("sampleheight", updateButton)
	val instrUriInput = new UriOptInput("instrumenturi", updateButton)
	val timeIntevalInput = new TimeIntevalInput(acqStartInput, acqStopInput)

	val collectionTitle = new TextInput("collectiontitle", updateButton)
	val collectionDescription = new OptTextArea("collectiondescription", updateButton)
	val collectionMembers = new UriListInput("collectionmembers", updateButton)

	def dto: Try[UploadDto] = typeControl.value match {
		case Some("data") => dataObjectDto
		case Some("document") => documentObjectDto
		case Some("collection") => staticCollectionDto
		case _ => dataObjectDto
	}
	private def isTypeSelected = if (typeControl.value.isEmpty) fail("No file type selected") else Success(())

	def dataObjectDto: Try[DataObjectDto] = for(
		file <- fileInput.file;
		hash <- fileInput.hash;
		_ <- isTypeSelected;
		previousVersion <- previousVersionInput.value.withErrorContext("Previous version");
		doi <- existingDoiInput.value.withErrorContext("Pre-existing DOI");
		station <- stationSelect.value.withErrorContext("Station");
		objSpec <- objSpecSelect.value.withErrorContext("Data type");
		submitter <- submitterIdSelect.value.withErrorContext("Submitter Id");
		acqInterval <- timeIntevalInput.value.withErrorContext("Acqusition time interval");
		nRows <- nRowsInput.value.withErrorContext("Number of rows");
		samplingHeight <- samplingHeightInput.value.withErrorContext("Sampling height");
		instrumentUri <- instrUriInput.value.withErrorContext("Instrument URI")
	) yield DataObjectDto(
		hashSum = hash,
		submitterId = submitter.id,
		objectSpecification = objSpec.uri,
		fileName = file.name,
		specificInfo = Right(
			StationDataMetadata(
				station = station.uri,
				instrument = instrumentUri.map(Left(_)),
				samplingHeight = samplingHeight,
				acquisitionInterval = acqInterval,
				nRows = nRows,
				production = None
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
		submitter <- submitterIdSelect.value.withErrorContext("Submitter Id")
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
		members <- collectionMembers.value.withErrorContext("List of object urls");
		_ <- isTypeSelected;
		previousVersion <- previousVersionInput.value.withErrorContext("Previous version");
		doi <- existingDoiInput.value.withErrorContext("Pre-existing DOI");
		submitter <- submitterIdSelect.value.withErrorContext("Submitter Id")
	) yield StaticCollectionDto(
		submitterId = submitter.id,
		members = members,
		title = title,
		description = description,
		isNextVersionOf = previousVersion.map(Left(_)),
		preExistingDoi = doi
	)
}
