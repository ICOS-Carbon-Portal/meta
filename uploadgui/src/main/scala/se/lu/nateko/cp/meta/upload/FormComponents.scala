package se.lu.nateko.cp.meta.upload

import java.net.URI
import java.time.Instant

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{ Success, Try }
import scala.util.Failure

import org.scalajs.dom
import org.scalajs.dom.{ document, html }
import org.scalajs.dom.raw._

import Utils._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.TimeInterval

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

class FileInput(elemId: String, cb: () => Unit){
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
		queue.execute(() => fileInput.oninput(null))// no need to do this eagerly, just scheduling
	}
}

class Radio(elemId: String, cb: Int => Unit) {
	protected[this] val inputBlock: html.Element = getElementById[html.Element](elemId).get
	protected[this] var _value: Option[Int] = None

	def value: Option[Int] = _value

	inputBlock.onchange = _ => {
		_value = querySelector[html.Input](inputBlock, "input[type=radio]:checked").map(input => input.value.toInt)
		_value.foreach(cb)
	}

	if(querySelector[html.Input](inputBlock, "input[type=radio]:checked").isDefined){
		queue.execute(() => inputBlock.onchange(null))
	}
}

class TimeIntevalInput(fromInput: InstantInput, toInput: InstantInput){
	def value: Try[Option[TimeInterval]] =
		if(fromInput.isDisabled && toInput.isDisabled) Success(None) else
			for(
				from <- fromInput.value.withErrorContext("Acqusition start");
				to <- toInput.value.withErrorContext("Acqusition stop")
			) yield Some(TimeInterval(from, to))
}

abstract class GenericInput[T](elemId: String, cb: () => Unit, init: Try[T])(parser: String => Try[T]) {
	protected[this] val input: html.Input = getElementById[html.Input](elemId).get
	private[this] var _value: Try[T] = init

	def value: Try[T] = _value

	input.oninput = _ => {
		val oldValue = _value
		_value = parser(input.value)

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

	def isDisabled: Boolean = input.disabled

	if(!input.value.isEmpty){
		queue.execute(() => input.oninput(null))
	}
}

class InstantInput(elemId: String, cb: () => Unit) extends GenericInput[Instant](elemId, cb, fail("no timestamp provided"))(
	s => Try(Instant.parse(s))
)

class GenericOptionalInput[T](elemId: String, cb: () => Unit)(parser: String => Try[Option[T]])
		extends GenericInput[Option[T]](elemId, cb, Success(None))(
	s => if (s == null || s.trim.isEmpty) Success(None) else parser(s.trim)
)

class HashOptInput(elemId: String, cb: () => Unit)
	extends GenericOptionalInput[Sha256Sum](elemId, cb)(s => Sha256Sum.fromString(s).map(Some(_)))

class IntOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[Int](elemId, cb)(s => Try(Some(s.toInt)))
class FloatOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[Float](elemId, cb)(s => Try(Some(s.toFloat)))
class UriOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[URI](elemId, cb)(s => {
	if(s.startsWith("https://") || s.startsWith("http://")) Try(Some(new URI(s)))
	else Failure(new Exception("Malformed URL (must start with http[s]://)"))
})

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

class DataElements() {
	def show(): Unit = {
		dom.document.querySelectorAll(".data-section").asInstanceOf[NodeListOf[HTMLElement]].map { section =>
			section.style.display = "block"
		}
	}

	def hide(): Unit = {
		dom.document.querySelectorAll(".data-section").asInstanceOf[NodeListOf[HTMLElement]].map { section =>
			section.style.display = "none"
		}
	}
}
