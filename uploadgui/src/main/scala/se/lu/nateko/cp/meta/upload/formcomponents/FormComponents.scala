package se.lu.nateko.cp.meta.upload.formcomponents

import java.time.Instant

import scala.util.{ Success, Try, Failure }

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.ext.*

import se.lu.nateko.cp.meta.upload.Utils.*
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.core.data.OneOrSeq


class FormElement(elemId: String) {
	private val form = getElementById[html.Form](elemId).get

	def reset() = {
		form.reset()
	}
}

class ProgressBar(elemId: String){
	private val progressBar = new HtmlElements(elemId)

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
	i => i.toString
)
class InstantOptInput(elemId: String, cb: () => Unit) extends GenericTextInput[Option[Instant]](elemId, cb, Success(None))(
	s => if(s.trim.isEmpty) Success(None) else Try(Some(Instant.parse(s.trim))),
	_.fold("")(_.toString)
)
class TextInput(elemId: String, cb: () => Unit, hint: String) extends GenericTextInput[String](elemId, cb, fail(s"Missing $hint"))(s => Try(s), s => s)


class HashOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[Sha256Sum](elemId, cb)(
	s =>
		if(s.trim.isEmpty) Success(None)
		else Sha256Sum.fromString(s.trim).map(Some(_)),
	hash => hash.id
)

class HashOptOneOrManyInput(elemId: String, cb: () => Unit)
	extends GenericOptionalInput[OneOrSeq[Sha256Sum]](elemId, cb)(
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
class DoubleInput(elemId: String, cb: () => Unit) extends GenericTextInput[Double](elemId, cb, fail("not a double number"))(
	s => Try(s.toDouble), _.toString()
)

class DoiOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[Doi](elemId, cb)(s => Doi.parse(s) match {
	case Success(doi) => Success(Some(doi))
	case Failure(err) => if (s.isEmpty) Success(None) else Failure(err)
}, _.toString())
class DescriptionInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[String](elemId, cb)(s => {
	if s.length > 5000 then fail("Description too long") else Try(Some(s))
}, _.toString())
class TextOptInput(elemId: String, cb: () => Unit) extends GenericOptionalInput[String](elemId, cb)(s => Try(Some(s)), _.toString())

class Button(elemId: String, onClick: () => Unit){
	private val button = getElementById[html.Button](elemId).get
	private var popover = initializeBootstrapPopover(button.parentElement)

	def enable(): Unit =
		button.disabled = false
		popover.disable()

	def disable(errMessage: String): Unit =
		button.disabled = true
		button.parentElement.setAttribute("data-bs-content", errMessage)
		if errMessage.nonEmpty then
			popover.dispose()
			popover = initializeBootstrapPopover(button.parentElement)
		else popover.disable()


	button.onclick = _ => onClick()
}

class HtmlElements(selector: String) {
	private var enabled = false
	def areEnabled: Boolean = enabled

	def show(): Unit = {
		dom.document.querySelectorAll(selector).foreach {
			case section: dom.HTMLElement =>
				section.style.display = "block"
		}
		enabled = true
	}

	def hide(): Unit = {
		dom.document.querySelectorAll(selector).foreach {
			case section: dom.HTMLElement =>
				section.style.display = "none"
		}
		enabled = false
	}

	def enable(): Unit = {
		dom.document.querySelectorAll(selector).foreach {
			case section: dom.HTMLElement =>
				section.style.color = "black"
		}
		enabled = true
	}
	def disable(): Unit = {
		dom.document.querySelectorAll(selector).foreach {
			case section: dom.HTMLElement =>
				section.style.color = "gray"
		}
		enabled = false
	}
}

class TagCloud(elemId: String) {
	private val div = getElementById[html.Div](elemId).get

	def setList(keywords: Seq[String]): Unit = {
		div.innerHTML =
			if (keywords.isEmpty)
				"None"
			else
				keywords.map(keyword => s"""<span class="badge rounded-pill bg-secondary">$keyword</span>""").mkString(" ")
	}
}

class Modal(elemId: String) {
	private val modal = getElementById[html.Div](elemId).get
	private val title = querySelector[html.Heading](modal, ".modal-title").get
	private val body = querySelector[html.Div](modal, ".modal-body").get

	def setTitle(text: String): Unit = {
		title.innerText = text
	}

	def setBody(html: String): Unit = {
		body.innerHTML = html
	}
}

class Checkbox(elemId: String, cb: (Boolean) => Unit) {
	private val checkbox = getElementById[html.Input](elemId).get

	def checked: Boolean = checkbox.checked
	def check(): Unit = {
		checkbox.checked = true
		cb(checked)
	}
	def uncheck(): Unit = {
		checkbox.checked = false
		cb(checked)
	}

	def enable(): Unit = checkbox.disabled = false
	def disable(): Unit = checkbox.disabled = true

	checkbox.onchange = _ => cb(checked)
}

class Text(elemId: String):
	private val label = getElementById[html.Span](elemId).get

	def setText(text: String): Unit =
		label.innerHTML = text
