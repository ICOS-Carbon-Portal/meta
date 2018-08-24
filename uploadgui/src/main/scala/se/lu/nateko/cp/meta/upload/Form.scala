package se.lu.nateko.cp.meta.upload

import java.time.Instant

import org.scalajs.dom
import org.scalajs.dom.{document, html}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.upload.Utils._
import se.lu.nateko.cp.meta.{StationDataMetadata, SubmitterProfile, UploadMetadataDto}

import scala.concurrent.ExecutionContext
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import Utils._

class Form(
	onUpload: () => Unit,
	onSubmitterSelect: SubmitterProfile => Future[IndexedSeq[Station]]
) {

	val button = new SubmitButton("submitbutton", onUpload)
	val updateButton: () => Unit = () => dto match{
		case Success(_) => button.enable()
		case Failure(err) => button.disable(err.getMessage)
	}

	val onSubmitterSelected: () => Unit = () =>
		submitterIdSelect.value.foreach{submitter =>
			whenDone(onSubmitterSelect(submitter)){stations =>
				stationSelect.setOptions(stations)
				updateButton()
			}
		}

	val fileInput = new FileInput("fileinput", updateButton)

	val stationSelect = new Select[Station]("stationselect", s => s"${s.id} (${s.name})", updateButton)
	val objSpecSelect = new Select[ObjSpec]("objspecselect", _.name, updateButton)
	val submitterIdSelect = new Select[SubmitterProfile]("submitteridselect", _.id, onSubmitterSelected)

	val acqStartInput = new InstantInput("acqstartinput", updateButton)
	val acqStopInput = new InstantInput("acqstopinput", updateButton)

	def dto: Try[UploadMetadataDto] = for(
		file <- fileInput.file;
		hash <- fileInput.hash;
		station <- stationSelect.value.withErrorContext("Station");
		objSpec <- objSpecSelect.value.withErrorContext("Data type");
		submitter <- submitterIdSelect.value.withErrorContext("Submitter Id");
		acqStart <- acqStartInput.instant.withErrorContext("Acqusition start");
		acqStop <- acqStopInput.instant.withErrorContext("Acqusition stop")
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
				acquisitionInterval = Some(TimeInterval(acqStart, acqStop)),
				nRows = None,
				production = None
			)
		),
		isNextVersionOf = None,
		preExistingDoi = None
	)
}

class Select[T](elemId: String, labeller: T => String, cb: () => Unit){
	private val select = getElement[html.Select](elemId).get
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
	private val fileInput = getElement[html.Input](elemId).get
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

class InstantInput(elemId: String, cb: () => Unit)(implicit ctxt: ExecutionContext){
	private[this] val input = getElement[html.Input](elemId).get
	private[this] var _instant: Try[Instant] = fail("no timestamp provided")

	def instant = _instant

	input.oninput = _ => {
		val oldInstant = _instant
		_instant = Try(Instant.parse(input.value))

		if(_instant.isSuccess || input.value.isEmpty){
			input.style.backgroundColor = null
			input.title = ""
		} else {
			input.style.backgroundColor = "lightcoral"
			input.title = _instant.failed.map(_.getMessage).getOrElse("")
		}

		if(oldInstant.isSuccess != _instant.isSuccess) cb()
	}

	if(!input.value.isEmpty){
		ctxt.execute(() => input.oninput(null))
	}
}

class SubmitButton(elemId: String, onSubmit: () => Unit){
	private[this] val button = getElement[html.Button](elemId).get

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
