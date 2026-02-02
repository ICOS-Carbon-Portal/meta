package se.lu.nateko.cp.meta.upload.formcomponents

import scala.util.{Try, Success}
import org.scalajs.dom.html
import se.lu.nateko.cp.meta.upload.Utils.*
import scala.collection.mutable
import se.lu.nateko.cp.meta.upload.DatasetVar

class L3VarInfoForm(elemId: String, notifyUpdate: () => Unit) {

	var list: IndexedSeq[DatasetVar] = IndexedSeq.empty

	def values: Try[Option[Seq[DatasetVar]]] = if(elems.isEmpty) Success(None) else Try{
		Some(elems.map(_.varInfo.get).toIndexedSeq)
	}

	def setValues(vars: Option[Seq[DatasetVar]]): Unit = {
		elems.foreach(_.remove())
		elems.clear()
		vars.foreach{vdtos =>
			vdtos.foreach{vdto =>
				val input = new L3VarInfoInput
				input.setValue(vdto)
				elems.append(input)
			}
		}
	}

	private val formDiv = getElementById[html.Div](elemId)
	private val template = querySelector[html.Div](formDiv, ".l3varinfo-element").get
	private var _ordId: Long = 0L

	private val elems = mutable.Buffer.empty[L3VarInfoInput]

	querySelector[html.Button](formDiv, "#l3varadd-button").foreach{
		_.onclick = _ => {
			elems.append(new L3VarInfoInput)
		}
	}

	def show(): Unit = {
		formDiv.style.display = "block"
	}

	def hide(): Unit = {
		formDiv.style.display = "none"
	}

	private class L3VarInfoInput{

		def varInfo: Try[DatasetVar] = varNameInput.value.withMissingError("Missing variable name")

		def setValue(varName: DatasetVar): Unit = {
			varNameInput.value = varName
		}

		def remove(): Unit = {
			formDiv.removeChild(div)
			notifyUpdate()
		}

		private val id: Long = {_ordId += 1; _ordId}
		private val div = deepClone(template)

		formDiv.appendChild(div)

		querySelector[html.Button](div, ".varInfoButton").foreach{button =>
			button.onclick = _ => remove()
		}

		Seq("varnameInput").foreach{inputClass =>
			querySelector[html.Select](div, s".$inputClass").foreach{_.id = s"${inputClass}_$id"}
		}

		private val varNameInput = new Select[DatasetVar](s"varnameInput_$id", _.label, _.uri.toString, false, notifyUpdate)
		varNameInput.setOptions(list)

		div.style.display = ""

	}
}
