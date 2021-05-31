package se.lu.nateko.cp.meta.upload.formcomponents

import org.scalajs.dom.{html, document}
import se.lu.nateko.cp.meta.upload.Utils._
import scala.util.Success
import scala.util.Try
import scala.collection.mutable
import DataListInput.listFailure

class DataListInput[T](elemId: String, list: DataList[T], cb: () => Unit) extends GenericTextInput[T](elemId, cb, listFailure(""))(
	s => list.lookupValue(s).map(Success(_)).getOrElse(listFailure(s)),
	list.labeller
)

object DataListInput {
	def listFailure(label: String) =
		if(label.trim.isEmpty) fail(s"No value chosen")
		else fail(s"'$label' not found")
}

class DataList[T](elemId: String, val labeller: T => String) {
	private val list = getElementById[html.DataList](elemId).get
	private[this] var _values = IndexedSeq.empty[T]
	private val valLookup = mutable.Map.empty[String, T]

	def lookupValue(label: String): Option[T] = valLookup.get(label)

	def values = _values

	def values_=(values: IndexedSeq[T]): Unit = {
		list.innerHTML = ""
		_values = values
		valLookup.clear()

		values.foreach{ value =>
			valLookup.addOne(labeller(value), value)
			val opt = document.createElement("option")
			opt.textContent = labeller(value)
			list.appendChild(opt)
		}
	}
}

class DataListForm[T](elemId: String, list: DataList[T], notifyUpdate: () => Unit) {

	def values: Try[Seq[T]] = if(elems.isEmpty) Success(Seq()) else Try{
		elems.map(_.value.get).toIndexedSeq
	}

	def setValues(vars: Seq[T]): Unit = {
		elems.foreach(_.remove())
		elems.clear()
		vars.foreach{vdto =>
			val input = new DataListEditableInput(list)
			input.value = vdto
			elems.append(input)
		}
	}

	private [this] val formDiv = getElementById[html.Div](elemId).get
	private [this] val template = querySelector[html.Div](formDiv, ".contributors-element").get
	private [this] var _ordId: Long = 0L
	private [this] val addButton = querySelector[html.Button](formDiv, "#add-contributor").get

	private[this] val elems = mutable.Buffer.empty[DataListEditableInput[T]]

	addButton.onclick = _ => {
		elems.append(new DataListEditableInput(list)).foreach(_.focus())
		notifyUpdate()
	}

	private class DataListEditableInput[T](list: DataList[T]) {

		def value: Try[T] = dataListInput.value
		def isEmpty: Boolean = dataListInput.isEmpty

		def value_=(value: T) = {
			dataListInput.value = value
		}

		def remove(): Unit = {
			formDiv.removeChild(div)
		}

		def focus(): Unit = {
			dataListInput.focus()
		}

		private[this] val id: Long = {_ordId += 1; _ordId}
		val div = deepClone(template)

		formDiv.insertBefore(div, addButton)

		querySelector[html.Button](div, ".remove-data-list-input").foreach{button =>
			button.onclick = _ => {
				elems.remove(elems.indexOf(this))
				remove()
				notifyUpdate()
			}
		}

		Seq("data-list-input").foreach{inputClass =>
			querySelector[html.Input](div, s".$inputClass").foreach{_.id = s"${inputClass}-$id"}
		}

		private val dataListInput = new DataListInput[T](s"data-list-input-$id", list, notifyUpdate)

		div.style.display = ""

	}

}
