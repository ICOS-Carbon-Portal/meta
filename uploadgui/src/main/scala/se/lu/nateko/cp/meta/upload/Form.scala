package se.lu.nateko.cp.meta.upload

import java.time.Instant

import org.scalajs.dom
import org.scalajs.dom.{document, html}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{Envri, TimeInterval}
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.{StationDataMetadata, SubmitterProfile, UploadMetadataDto}

import scala.concurrent.ExecutionContext
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

	val previousVersionInput = new HashInput("previoushash", updateButton)
	val levelControl = new Radio("level-radio", onLevelSelected)
	val stationSelect = new Select[Station]("stationselect", s => s"${s.id} (${s.name})", updateButton)
	val objSpecSelect = new Select[ObjSpec]("objspecselect", _.name, updateButton)
	val nRowsInput = new NRowsInput("nrows", updateButton)

	val submitterIdSelect = new Select[SubmitterProfile]("submitteridselect", _.id, onSubmitterSelected)

	val acqStartInput = new InstantInput("acqstartinput", updateButton)
	val acqStopInput = new InstantInput("acqstopinput", updateButton)

	val timeIntevalInput = new TimeIntevalInput(acqStartInput, acqStopInput, levelControl)
	def dto: Try[UploadMetadataDto] = for(
		file <- fileInput.file;
		hash <- fileInput.hash;
		previousVersion <- previousVersionInput.value.withErrorContext("Previous version");
		station <- stationSelect.value.withErrorContext("Station");
		objSpec <- objSpecSelect.value.withErrorContext("Data type");
		submitter <- submitterIdSelect.value.withErrorContext("Submitter Id");
		acqInterval <- timeIntevalInput.value.withErrorContext("Acqusition time interfal");
		nRows <- nRowsInput.value.withErrorContext("Number of rows")
	) yield UploadMetadataDto(
		hashSum = hash,
		submitterId = submitter.id,
		objectSpecification = objSpec.uri,
		fileName = file.name,
		specificInfo = Right(
			StationDataMetadata(
				station = station.uri,
				instrument = None,
				samplingHeight = None,
				acquisitionInterval = acqInterval,
				nRows = nRows,
				production = None
			)
		),
		isNextVersionOf = previousVersion,
		preExistingDoi = None
	)
}

class Select[T](elemId: String, labeller: T => String, cb: () => Unit){
	private val select = getElementById[html.Select](elemId).get
	private var _values: IndexedSeq[T] = IndexedSeq.empty

	select.onchange = _ => cb()

	def value: Try[T] = {
		val idx = select.selectedIndex
		if(idx < 0 || idx >= _values.length) fail("no option chosen") else Success(_values(idx))
	}

	def setOptions(values: IndexedSeq[T]): Unit = {
		select.innerHTML = ""
		_values = values

		values.foreach{value =>
			val opt = document.createElement("option")
			opt.appendChild(document.createTextNode(labeller(value)))
			select.appendChild(opt)
		}

		// Select option if only one choice
		if (values.size == 1) {
			select.selectedIndex = 0
			cb()
		} else {
			select.selectedIndex = -1
		}

		select.disabled = values.isEmpty
	}
}

class FileInput(elemId: String, cb: () => Unit)(implicit ctxt: ExecutionContext){
	private val fileInput = getElementById[html.Input](elemId).get
	private var _hash: Try[Sha256Sum] = file.flatMap(_ => fail("hashsum computing has not started yet"))

	def file: Try[dom.File] = if(fileInput.files.length > 0) Success(fileInput.files(0)) else fail("no file chosen")
	def hash: Try[Sha256Sum] = _hash

	fileInput.oninput = _ => file.foreach{f =>
		if(_hash.isSuccess){
			_hash = fail("hashsum is being computed")
			cb()
		}
		whenDone(FileHasher.hash(f)){hash =>
			if(file.toOption.contains(f)) {
				_hash = Success(hash)//file could have been changed while digesting for SHA-256
				cb()
			}
		}
	}

	if(file.isSuccess){//pre-chosen file, e.g. due to browser page reload
		ctxt.execute(() => fileInput.oninput(null))// no need to do this eagerly, just scheduling
	}
}

class Radio(elemId: String, cb: Int => Unit)(implicit ctxt: ExecutionContext) {
	protected[this] val inputBlock: html.Element = getElementById[html.Element](elemId).get
	protected[this] var _value: Option[Int] = None

	def value: Option[Int] = _value

	inputBlock.onchange = _ => {
		_value = querySelector[html.Input](inputBlock, "input[type=radio]:checked").map(input => input.value.toInt)
		_value.foreach(cb)
	}

	if(querySelector[html.Input](inputBlock, "input[type=radio]:checked").isDefined){
		ctxt.execute(() => inputBlock.onchange(null))
	}
}

class TimeIntevalInput(fromInput: InstantInput, toInput: InstantInput, level: Radio){
	def value: Try[Option[TimeInterval]] = level.value match {
		case Some(0) =>
			for(
				from <- fromInput.value.withErrorContext("Acqusition start");
				to <- toInput.value.withErrorContext("Acqusition stop")
			) yield Some(TimeInterval(from, to))
		case _ => Success(None)
	}
}

abstract class GenericInput[T](elemId: String, cb: () => Unit)(implicit ctxt: ExecutionContext) {
	protected[this] val input: html.Input = getElementById[html.Input](elemId).get
	protected[this] var _value: Try[T]

	def value: Try[T]

	input.oninput = _ => {
		val oldValue = _value
		_value = value

		if (_value.isSuccess || input.value.isEmpty) {
			input.title = ""
			input.parentElement.classList.remove("has-error")
		} else {
			input.title = _value.failed.map(_.getMessage).getOrElse("")
			input.parentElement.classList.add("has-error")
		}

		if(oldValue.isSuccess != _value.isSuccess) cb()
	}

	def enable(): Unit = {
		input.disabled = false
	}

	def disable(): Unit = {
		input.disabled = true
	}

	if(!input.value.isEmpty){
		ctxt.execute(() => input.oninput(null))
	}
}

class InstantInput(elemId: String, cb: () => Unit) extends GenericInput[Instant](elemId: String, cb: () => Unit) {
	override protected var _value: Try[Instant] = fail("no timestamp provided")
	override def value: Try[Instant] = Try(Instant.parse(input.value))
}

class HashInput(elemId: String, cb: () => Unit) extends GenericInput[Option[Sha256Sum]](elemId: String, cb: () => Unit) {
	override protected var _value: Try[Option[Sha256Sum]] = Success(None)
	override def value: Try[Option[Sha256Sum]] = {
		if (input.value == null || input.value.trim.isEmpty) Success(None)
		else Sha256Sum.fromString(input.value).map(Some(_))
	}
}

class NRowsInput(elemId: String, cb: () => Unit) extends GenericInput[Option[Int]](elemId: String, cb: () => Unit) {
	override protected[this] var _value: Try[Option[Int]] = Success(None)
	override def value: Try[Option[Int]] = {
		if (input.value == null || input.value.trim.isEmpty) Success(None)
		else Try(Some(input.value.toInt))
	}
}

class SubmitButton(elemId: String, onSubmit: () => Unit){
	private[this] val button = getElementById[html.Button](elemId).get

	def enable(): Unit = {
		button.disabled = false
		button.title = ""
	}

	def disable(errMessage: String): Unit = {
		button.disabled = true
		button.title = errMessage
	}

	button.disabled = true

	button.onclick = _ => onSubmit()
}
