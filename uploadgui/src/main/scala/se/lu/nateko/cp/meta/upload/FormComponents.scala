package se.lu.nateko.cp.meta.upload

import java.time.Instant

import org.scalajs.dom
import org.scalajs.dom.{document, html}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.upload.Utils.{fail, getElementById, querySelector, whenDone}

import scala.concurrent.ExecutionContext
import scala.util.{Success, Try}
import Utils._

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

class InstantInput(elemId: String, cb: () => Unit)(implicit ctxt: ExecutionContext) extends GenericInput[Instant](elemId: String, cb: () => Unit) {
	override protected var _value: Try[Instant] = fail("no timestamp provided")
	override def value: Try[Instant] = Try(Instant.parse(input.value))
}

class HashInput(elemId: String, cb: () => Unit)(implicit ctxt: ExecutionContext) extends GenericInput[Option[Sha256Sum]](elemId: String, cb: () => Unit) {
	override protected var _value: Try[Option[Sha256Sum]] = Success(None)
	override def value: Try[Option[Sha256Sum]] = {
		if (input.value == null || input.value.trim.isEmpty) Success(None)
		else Sha256Sum.fromString(input.value).map(Some(_))
	}
}

class NRowsInput(elemId: String, cb: () => Unit)(implicit ctxt: ExecutionContext) extends GenericInput[Option[Int]](elemId: String, cb: () => Unit) {
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
