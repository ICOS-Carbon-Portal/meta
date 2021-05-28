package se.lu.nateko.cp.meta.upload.formcomponents

import scala.util.{Try, Success}
import org.scalajs.dom.html
import se.lu.nateko.cp.meta.upload.Utils._



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

	input.oninput = _ => refreshAndNotify()

	def refreshAndNotify(): Unit = {
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

	def focus(): Unit = {
		input.focus()
	}

	def isDisabled: Boolean = input.disabled
	def isEmpty: Boolean = input.value.isEmpty()

}

class GenericOptionalInput[T](elemId: String, cb: () => Unit)(parser: String => Try[Option[T]], serializer: T => String)
		extends GenericTextInput[Option[T]](elemId, cb, Success(None))(
	s => if (s == null || s.trim.isEmpty) Success(None) else parser(s.trim),
	_.fold("")(serializer(_))
)
