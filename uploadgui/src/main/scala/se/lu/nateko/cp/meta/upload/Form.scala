package se.lu.nateko.cp.meta.upload

import org.scalajs.dom
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.{StationDataMetadata, SubmitterProfile, UploadMetadataDto}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import Utils._

class Form(
	onUpload: (UploadMetadataDto, dom.File) => Unit,
	onSubmitterSelect: SubmitterProfile => Future[IndexedSeq[Station]]
)(implicit envri: Envri.Envri, envriConf: EnvriConfig) {

	def submitAction(): Unit = for(dto <- dto; file <- fileInput.file; nRows <- nRowsInput.value) {
		whenDone(Backend.tryIngestion(file, dto.objectSpecification, nRows)){ msg =>
			if (msg.isEmpty) {
				onUpload(dto, file)
			} else {
				showAlert(msg, "alert alert-error")
			}
		}
	}
	val button = new SubmitButton("submitbutton", () => submitAction())
	val updateButton: () => Unit = () => dto match{
		case Success(_) => button.enable()
		case Failure(err) => button.disable(err.getMessage)
	}

	val onLevelSelected: Int => Unit = (level: Int) => {
		whenDone{
			Backend.getObjSpecs.map(_.filter(_.dataLevel == level))
		}(objSpecSelect.setOptions)
		if (level == 0) {
			nRowsInput.disable()
			acqStartInput.enable()
			acqStopInput.enable()
		} else {
			nRowsInput.enable()
			acqStartInput.disable()
			acqStopInput.disable()
		}
	}

	val onSubmitterSelected: () => Unit = () =>
		submitterIdSelect.value.foreach{submitter =>
			whenDone(onSubmitterSelect(submitter)){stations =>
				stationSelect.setOptions(stations)
				updateButton()
			}
		}

	val fileInput = new FileInput("fileinput", updateButton)

	val previousVersionInput = new HashOptInput("previoushash", updateButton)
	val levelControl = new Radio("level-radio", onLevelSelected)
	val stationSelect = new Select[Station]("stationselect", s => s"${s.id} (${s.name})", updateButton)
	val objSpecSelect = new Select[ObjSpec]("objspecselect", _.name, updateButton)
	val nRowsInput = new IntOptInput("nrows", updateButton)

	val submitterIdSelect = new Select[SubmitterProfile]("submitteridselect", _.id, onSubmitterSelected)

	val acqStartInput = new InstantInput("acqstartinput", updateButton)
	val acqStopInput = new InstantInput("acqstopinput", updateButton)
	val samplingHeightInput = new FloatOptInput("sampleheight", updateButton)
	val instrUriInput = new UriOptInput("instrumenturi", updateButton)

	val timeIntevalInput = new TimeIntevalInput(acqStartInput, acqStopInput, levelControl)
	def dto: Try[UploadMetadataDto] = for(
		file <- fileInput.file;
		hash <- fileInput.hash;
		previousVersion <- previousVersionInput.value.withErrorContext("Previous version");
		station <- stationSelect.value.withErrorContext("Station");
		objSpec <- objSpecSelect.value.withErrorContext("Data type");
		submitter <- submitterIdSelect.value.withErrorContext("Submitter Id");
		acqInterval <- timeIntevalInput.value.withErrorContext("Acqusition time interfal");
		nRows <- nRowsInput.value.withErrorContext("Number of rows");
		samplingHeight <- samplingHeightInput.value.withErrorContext("Sampling height");
		instrumentUri <- instrUriInput.value.withErrorContext("Instrument URI")
	) yield UploadMetadataDto(
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
		isNextVersionOf = previousVersion,
		preExistingDoi = None
	)
}

