package se.lu.nateko.cp.meta.upload.formcomponents

import java.time.Instant

import scala.util.{ Success, Try, Failure }

import org.scalajs.dom
import org.scalajs.dom.{ document, html }
import org.scalajs.dom.raw._
import org.scalajs.dom.ext._
import scala.scalajs.js

import se.lu.nateko.cp.meta.upload.Utils._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.doi.Doi


class FormElement(elemId: String) {
	private val form = getElementById[html.Form](elemId).get

	def reset() = {
		form.reset()
	}
}

class ProgressBar(elemId: String){
	private[this] val progressBar = new HtmlElements(elemId)

	def show(): Unit = {
		progressBar.show()
	}

	def hide(): Unit = {
		progressBar.hide()
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



class InstantInput(elemId: String, cb: () => Unit) extends GenericTextInput[Instant](elemId, cb, fail("no timestamp provided"))(
	s => Try(Instant.parse(s)),
	i => i.toString()
)
class TextInput(elemId: String, cb: () => Unit) extends GenericTextInput[String](elemId, cb, fail("Missing title"))(s => Try(s), s => s)

class HashOptInput(elemId: String, cb: () => Unit)
	extends GenericOptionalInput[Either[Sha256Sum, Seq[Sha256Sum]]](elemId, cb)(
		s =>
			if(s.isEmpty) Success(None)
			else if(s.contains("\n")) Try(Some(Right(s.split("\n").map(line => Sha256Sum.fromString(line).get).toIndexedSeq)))
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
			else Try(Some(s.split("\n").map(Sha256Sum.fromString(_).get).toIndexedSeq)),
		shaSeq => shaSeq.map(_.id).mkString("\n")
	)

class IntOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[Int](elemId, cb)(s => Try(Some(s.toInt)), _.toString())
class FloatOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[Float](elemId, cb)(s => Try(Some(s.toFloat)), _.toString())
class DoubleOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[Double](elemId, cb)(s => Try(Some(s.toDouble)), _.toString())

class DoiOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[Doi](elemId, cb)(s => Doi.parse(s) match {
	case Success(doi) => Success(Some(doi))
	case Failure(err) => if (s.isEmpty) Success(None) else Failure(err)
}, _.toString())
class TextOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[String](elemId, cb)(s => Try(Some(s)), _.toString())

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
