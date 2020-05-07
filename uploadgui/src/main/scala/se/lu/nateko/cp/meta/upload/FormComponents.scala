package se.lu.nateko.cp.meta.upload

import java.net.URI
import java.time.Instant

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{ Success, Try }
import scala.util.Failure
import scala.concurrent.Future

import org.scalajs.dom
import org.scalajs.dom.{ document, html }
import org.scalajs.dom.raw._
import org.scalajs.dom.ext._
import scala.scalajs.js

import Utils._
import FormTypeRadio._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.doi.Doi

class FormElement(elemId: String) {
	private val form = getElementById[html.Form](elemId).get

	def reset() = {
		form.reset()
	}
}

class Select[T](elemId: String, labeller: T => String, autoselect: Boolean = false, cb: () => Unit){
	private val select = getElementById[html.Select](elemId).get
	private var _values: IndexedSeq[T] = IndexedSeq.empty

	select.onchange = _ => cb()
	getElementById[html.Form]("form-block").get.onreset = _ => {
		select.selectedIndex = -1
		cb()
	}

	def value: Option[T] = {
		val idx = select.selectedIndex
		if(idx < 0 || idx >= _values.length) None else Some(_values(idx))
	}

	def value_=(t: T): Unit = select.selectedIndex = _values.indexOf(t)

	def setOptions(values: IndexedSeq[T]): Unit = {
		select.innerHTML = ""
		_values = values

		values.foreach{value =>
			val opt = document.createElement("option")
			opt.appendChild(document.createTextNode(labeller(value)))
			select.appendChild(opt)
		}

		// Select option if only one choice
		if (autoselect && values.size == 1) {
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
	private var _lastModified: Double = 0

	def file: Try[dom.File] = if(fileInput.files.length > 0) Success(fileInput.files(0)) else fail("no file chosen")

	def hash: Try[Sha256Sum] = {
		if (hasBeenModified) {
			fail("The file has been modified, please choose the updated version")
		} else {
			_hash
		}
	}

	def hasBeenModified: Boolean =
		file.map(getLastModified(_)) != Success(_lastModified)

	def rehash: Future[Sha256Sum] = {
		Future.fromTry(file).flatMap { f =>
			FileHasher.hash(f).flatMap{ hash =>
					_hash = Success(hash)
					_lastModified = getLastModified(f)
					Future(hash)
			}
		}
	}

	// The event is not dispatched if the file selected is the same as before
	fileInput.onchange = _ => file.foreach{f =>
		if(_hash.isSuccess){
			_hash = fail("hashsum is being computed")
			cb()
		}
		whenDone(FileHasher.hash(f)){hash =>
			if(file.toOption.contains(f)) {
				_hash = Success(hash) //file could have been changed while digesting for SHA-256
				_lastModified = f.asInstanceOf[js.Dynamic].lastModified.asInstanceOf[Double]
				cb()
			}
		}
	}

	def enable(): Unit = {
		fileInput.disabled = false
	}

	def disable(): Unit = {
		fileInput.disabled = true
	}

	if(file.isSuccess){//pre-chosen file, e.g. due to browser page reload
		queue.execute(() => fileInput.onchange(null))// no need to do this eagerly, just scheduling
	}

	private def getLastModified(file: dom.File) =
		file.asInstanceOf[js.Dynamic].lastModified.asInstanceOf[Double]

}

class Radio[T](elemId: String, cb: String => Unit, serializer: T => String) {
	protected[this] val inputBlock: html.Element = getElementById[html.Element](elemId).get
	protected[this] var _value: Option[String] = None

	def value: Option[String] = _value
	def value_=(t: T): Unit = {
		inputBlock.querySelectorAll("input[type=radio]").map(_ match {
			case input: html.Input => {
				if (input.value == serializer(t)) {
					input.checked = true
					_value = Some(input.value)
				}
			}
		})
	}

	def enable(): Unit = {
		inputBlock.querySelectorAll("input[type=radio]").map(_ match {
			case input: html.Input => input.disabled = false
		})
	}

	def disable(): Unit = {
		inputBlock.querySelectorAll("input[type=radio]").map(_ match {
			case input: html.Input => input.disabled = true
		})
	}

	inputBlock.onchange = _ => {
		_value = querySelector[html.Input](inputBlock, "input[type=radio]:checked").map(input => input.value)
		_value.foreach(cb)
	}

	getElementById[html.Form]("form-block").get.onreset = _ => {
		_value = None
		querySelector[html.Input](inputBlock, "input[type=radio]:checked").map(input => input.checked = false)
	}

	if(querySelector[html.Input](inputBlock, "input[type=radio]:checked").isDefined){
		queue.execute(() => inputBlock.onchange(null))
	}
}

class FormTypeRadio(elemId: String, cb: FormType => Unit) extends Radio[FormType](elemId, formTypeParser.andThen(cb), formTypeSerializer) {
	def formType: FormType = value.map(formTypeParser).getOrElse(defaultType)
}

object FormTypeRadio {
	sealed trait FormType
	case object Document extends FormType
	case object Data extends FormType
	case object Collection extends FormType

	val formTypeParser: String => FormType = _ match {
		case "data" => Data
		case "collection" => Collection
		case _ => defaultType
	}
	val defaultType: FormType = Document

	val formTypeSerializer: FormType => String = _ match {
		case Data => "data"
		case Collection => "collection"
		case Document => "document"
	}
}

class TimeIntevalInput(fromInput: InstantInput, toInput: InstantInput){
	def value_=(newIntOpt: Option[TimeInterval]): Unit = newIntOpt match {
		case None =>
			fromInput.reset()
			toInput.reset()
		case Some(newInt) =>
			fromInput.value = newInt.start
			toInput.value = newInt.stop
	}
	def value: Try[Option[TimeInterval]] =
		if(fromInput.isDisabled && toInput.isDisabled) Success(None) else
			for(
				from <- fromInput.value.withErrorContext("Acqusition start");
				to <- toInput.value.withErrorContext("Acqusition stop")
			) yield Some(TimeInterval(from, to))
}

private abstract class TextInputElement extends html.Element{
	var value: String
	var disabled: Boolean
}

abstract class GenericTextInput[T](elemId: String, cb: () => Unit, init: Try[T])(parser: String => Try[T], serializer: T => String) {
	private[this] val input: TextInputElement = getElementById[html.Element](elemId).get.asInstanceOf[TextInputElement]
	private[this] var _value: Try[T] = init

	def value: Try[T] = _value
	def value_=(t: T): Unit = {
		_value = Success(t)
		input.value = serializer(t)
	}
	def reset(): Unit = {
		_value = init
		input.value = ""
	}

	getElementById[html.Form]("form-block").get.onreset = _ => reset()
	input.oninput = _ => {
		_value = parser(input.value)

		if (_value.isSuccess || input.value.isEmpty) {
			input.title = ""
			input.parentElement.classList.remove("has-error")
		} else {
			input.title = _value.failed.map(_.getMessage).getOrElse("")
			input.parentElement.classList.add("has-error")
		}

		cb()
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

class InstantInput(elemId: String, cb: () => Unit) extends GenericTextInput[Instant](elemId, cb, fail("no timestamp provided"))(
	s => Try(Instant.parse(s)),
	i => i.toString()
)
class TextInput(elemId: String, cb: () => Unit) extends GenericTextInput[String](elemId, cb, fail("Missing title"))(s => Try(s), s => s)
class UriInput(elemId: String, cb: () => Unit) extends GenericTextInput[URI](elemId, cb, fail("Malformed URL (must start with http[s]://)"))(s => {
	if(s.startsWith("https://") || s.startsWith("http://")) Try(new URI(s))
	else Failure(new Exception("Malformed URL (must start with http[s]://)"))
}, uri => uri.toString())

class GenericOptionalInput[T](elemId: String, cb: () => Unit)(parser: String => Try[Option[T]], serializer: T => String)
		extends GenericTextInput[Option[T]](elemId, cb, Success(None))(
	s => if (s == null || s.trim.isEmpty) Success(None) else parser(s.trim),
	_.fold("")(serializer(_))
)

class HashOptInput(elemId: String, cb: () => Unit)
	extends GenericOptionalInput[Either[Sha256Sum, Seq[Sha256Sum]]](elemId, cb)(
		s =>
			if(s.isEmpty) Success(None)
			else if(s.contains("\n")) Try(Some(Right(s.split("\n").map(line => Sha256Sum.fromString(line).get))))
			else Try(Some(Left(Sha256Sum.fromString(s).get))),
		_ match {
			case Left(sha) => sha.id
			case Right(shaSeq) => shaSeq.map(_.id).mkString("\n")
		}
	)
class HashOptListInput(elemId: String, cb: () => Unit)
	extends GenericOptionalInput[Seq[Sha256Sum]](elemId, cb)(
		s =>
			if(s.isEmpty) Success(None)
			else Try(Some(s.split("\n").map(Sha256Sum.fromString(_).get))),
		shaSeq => shaSeq.map(_.id).mkString("\n")
	)

class IntOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[Int](elemId, cb)(s => Try(Some(s.toInt)), _.toString())
class FloatOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[Float](elemId, cb)(s => Try(Some(s.toFloat)), _.toString())

class UriOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[URI](elemId, cb)(UriInput.parser(_).map(Some(_)), _.toString())
class UriOptionalOneOrSeqInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[Either[URI, Seq[URI]]](elemId, cb)(s =>
	if(s.isEmpty) Success(None)
	else if(s.contains("\n")) Try(Some(Right(UriListInput.parser(s).get)))
	else Try(Some(Left(UriInput.parser(s).get))),
	_ match {
		case Left(value) => value.toString()
		case Right(value) => value.mkString("\n")
	})
object UriInput {
	def parser(s: String) = {
		if(s.startsWith("https://") || s.startsWith("http://")) Try(new URI(s))
		else Failure(new Exception("Malformed URL (must start with http[s]://)"))
	}
}
class DoiOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[Doi](elemId, cb)(s => Doi.parse(s) match {
	case Success(doi) => Success(Some(doi))
	case Failure(err) => if (s.isEmpty) Success(None) else Failure(err)
}, _.toString())
class TextOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[String](elemId, cb)(s => Try(Some(s)), _.toString())

class UriListInput(elemId: String, cb: () => Unit) extends GenericTextInput[Seq[URI]](elemId, cb, Success(Nil))(
	UriListInput.parser,
	UriListInput.serializer
)

class NonEmptyUriListInput(elemId: String, cb: () => Unit) extends GenericTextInput[Seq[URI]](elemId, cb, UriListInput.emptyError)(
	s => UriListInput.parser(s).flatMap(
		uris => if(uris.isEmpty) UriListInput.emptyError else Success(uris)
	),
	UriListInput.serializer
)

object UriListInput{

	def parser(value: String): Try[Seq[URI]] = Try(
		value.split("\n").map(_.trim).filterNot(_.isEmpty).map(line => {
			if (line.startsWith("https://") || line.startsWith("http://")) new URI(line)
			else throw new Exception("Malformed URL (must start with http[s]://)")
		})
	)

	def serializer = {
		list: Seq[URI] => list.map(_.toString).mkString("\n")
	}

	val emptyError = fail(s"uri list cannot be empty")
}

class Button(elemId: String, onClick: () => Unit){
	private[this] val button = getElementById[html.Button](elemId).get

	def enable(): Unit = {
		button.disabled = false
		button.title = ""
	}

	def disable(errMessage: String): Unit = {
		button.disabled = true
		button.title = errMessage
	}

	button.onclick = _ => onClick()
}

class HtmlElements(cssClass: String) {
	private[this] var enabled = false
	def areEnabled: Boolean = enabled

	def show(): Unit = {
		dom.document.querySelectorAll(cssClass).foreach {
			case section: HTMLElement =>
				section.style.display = "block"
		}
		enabled = true
	}

	def hide(): Unit = {
		dom.document.querySelectorAll(cssClass).foreach {
			case section: HTMLElement =>
				section.style.display = "none"
		}
		enabled = false
	}
}
