package se.lu.nateko.cp.meta.upload.formcomponents

import scala.language.unsafeNulls

import org.scalajs.dom.{html, document, DragEvent, DataTransferEffectAllowedKind}
import se.lu.nateko.cp.meta.upload.Utils.*
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
	private var _values = IndexedSeq.empty[T]
	private val valLookup = mutable.Map.empty[String, T]
	protected val lookupIsActive: Boolean = true

	def lookupValue(label: String): Option[T] = valLookup.get(label)

	def values = _values

	def values_=(values: IndexedSeq[T]): Unit = {
		list.innerHTML = ""
		_values = values
		valLookup.clear()

		values.foreach{ value =>
			if(lookupIsActive) valLookup.addOne(labeller(value), value)
			val opt = document.createElement("option")
			opt.textContent = labeller(value)
			list.appendChild(opt)
		}
	}
}

class KeywordDataList(elemId: String) extends DataList[String](elemId, identity) {
	override protected val lookupIsActive: Boolean = false
	override def lookupValue(label: String): Option[String] = Some(label)
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

	private val formDiv = getElementById[html.Div](elemId).get
	private val template = querySelector[html.Div](formDiv, ".data-list").get
	private var _ordId: Long = 0L
	private val addButton = querySelector[html.Button](formDiv, "#add-element").get

	private val elems = mutable.Buffer.empty[DataListEditableInput]

	addButton.onclick = _ => {
		elems.append(new DataListEditableInput(list)).foreach(_.focus())
		notifyUpdate()
	}

	private var draggedElement: DataListEditableInput = null

	private class DataListEditableInput(list: DataList[T]) {

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

		private val id: Long = {_ordId += 1; _ordId}
		val div = deepClone(template)

		formDiv.insertBefore(div, addButton.parentElement)

		querySelector[html.Button](div, ".remove-data-list-input").foreach{button =>
			button.onclick = _ => {
				elems.remove(elems.indexOf(this))
				remove()
				notifyUpdate()
			}
		}

		Seq("data-list-input").foreach{inputClass =>
			querySelector[html.Input](div, s".$inputClass").foreach{_.id = s"${inputClass}-$elemId-$id"}

			div.addEventListener("dragstart", { (e: DragEvent) =>
				draggedElement = this
				elems.foreach(_.div.classList.add("dropzone"))
				e.dataTransfer.effectAllowed = DataTransferEffectAllowedKind.move
				e.dataTransfer.setData("text/html", div.innerHTML)
			})

			div.addEventListener("dragend", { _ =>
				elems.foreach(_.div.classList.remove("dropzone"))
			})

			div.addEventListener("dragover", { (e: DragEvent) =>
				e.preventDefault()
				if (draggedElement != this) {
					var rect = this.div.getBoundingClientRect()
					var yPositionRelativeToElement  = e.clientY - this.div.getBoundingClientRect().top;
					val position = if yPositionRelativeToElement < this.div.clientHeight / 2 then "beforebegin" else "afterend"

					this.div.insertAdjacentElement(position, draggedElement.div)
				}
			})

			div.addEventListener("drop", { (e: DragEvent) =>
				e.stopPropagation()
				val oldPosition = elems.indexOf(draggedElement)
				val newPosition	 = draggedElement.div.parentElement.children.indexOf(draggedElement.div) - 1
				elems.remove(oldPosition)
				elems.insert(newPosition, draggedElement)
				false
			})
		}

		private val dataListInput = new DataListInput[T](s"data-list-input-$elemId-$id", list, notifyUpdate)

		div.style.display = ""

	}

}
