package se.lu.nateko.cp.meta.upload.formcomponents

import scala.util.{Try, Success}
import org.scalajs.dom.html
import se.lu.nateko.cp.meta.upload.Utils._
import scala.collection.mutable
import se.lu.nateko.cp.meta.L3VarDto

class L3VarInfoForm(elemId: String, notifyUpdate: () => Unit) {

	def varInfos: Try[Option[Seq[L3VarDto]]] = if(elems.isEmpty) Success(None) else Try{
		Some(elems.map(_.varInfo.get).toIndexedSeq)
	}

	def setValues(vars: Option[Seq[L3VarDto]]): Unit = {
		elems.foreach(_.remove())
		vars.foreach{vdtos =>
			vdtos.foreach{vdto =>
				val input = new L3VarInfoInput
				input.setValue(vdto)
				elems.append(input)
			}
		}
	}

	private [this] val formDiv = getElementById[html.Div](elemId).get
	private [this] val template = querySelector[html.Div](formDiv, ".l3varinfo-element").get
	private [this] var _ordId: Long = 0L

	private[this] val elems = mutable.Buffer.empty[L3VarInfoInput]

	private def onElemDeleted(elem: L3VarInfoInput): Unit =
		elems.remove(elems.indexOf(elem))

	querySelector[html.Button](formDiv, "#l3varadd-button").foreach{
		_.onclick = _ => {
			elems.append(new L3VarInfoInput)
		}
	}

	private class L3VarInfoInput{

		def varInfo: Try[L3VarDto] = for(
			varName <- varNameInput.value;
			minOpt <- minValueInput.value;
			maxOpt <- maxValueInput.value
		) yield L3VarDto(varName, minOpt.flatMap(min => maxOpt.map(min -> _)))

		def setValue(vdto: L3VarDto): Unit = {
			varNameInput.value = vdto.label
			minValueInput.value = vdto.minMax.map(_._1)
			maxValueInput.value = vdto.minMax.map(_._2)
		}

		def remove(): Unit = {
			formDiv.removeChild(div)
			onElemDeleted(this)
		}

		private[this] val id: Long = {_ordId += 1; _ordId}
		private[this] val div = deepClone(template)

		formDiv.appendChild(div)

		querySelector[html.Button](div, ".varInfoButton").foreach{button =>
			button.onclick = _ => remove()
		}

		Seq("varnameInput", "minValueInput", "maxValueInput").foreach{inputClass =>
			querySelector[html.Input](div, s".$inputClass").foreach{_.id = s"${inputClass}_$id"}
		}

		private val varNameInput = new TextInput(s"varnameInput_$id", notifyUpdate, "variable name")
		private val minValueInput = new DoubleOptInput(s"minValueInput_$id", notifyUpdate)
		private val maxValueInput = new DoubleOptInput(s"maxValueInput_$id", notifyUpdate)

		div.style.display = ""

	}
}
