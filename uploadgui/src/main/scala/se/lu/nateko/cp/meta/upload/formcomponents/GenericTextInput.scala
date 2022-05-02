package se.lu.nateko.cp.meta.upload.formcomponents

import scala.util.{Try, Success}
import org.scalajs.dom.html
import se.lu.nateko.cp.meta.upload.Utils.*



private abstract class TextInputElement extends html.Object{
	var value: String
	var disabled: Boolean
}

abstract class GenericTextInput[T](elemId: String, cb: () => Unit, init: Try[T])(parser: String => Try[T], serializer: T => String) {
	private val input: TextInputElement = getElementById[html.Element](elemId).get.asInstanceOf[TextInputElement]
	private var _value: Try[T] = init

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

		input.classList.remove("is-valid")
		input.classList.remove("is-invalid")
		input.setCustomValidity("")

		if (_value.isSuccess) {
			input.classList.add("is-valid")
		} else if (input.value.nonEmpty) {
			input.classList.add("is-invalid")
			input.setCustomValidity(_value.failed.map(_.getMessage).getOrElse(""))
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
