package se.lu.nateko.cp.meta.upload.formcomponents

import se.lu.nateko.cp.meta.upload.Utils._
import org.scalajs.dom.raw._
import org.scalajs.dom.html

class Radio[T](elemId: String, cb: T => Unit, parser: String => Option[T], serializer: T => String) {
	private[this] val inputBlock: html.Element = getElementById[html.Element](elemId).get
	private[this] val inputs: Seq[html.Input] = querySelectorAll(inputBlock, "input[type=radio]")

	def value: Option[T] = selectedInput.flatMap(si => parser(si.value))
	def value_=(t: T): Unit = inputs.iterator.foreach(inp => inp.checked = inp.value == serializer(t))

	def enable(): Unit = inputs.foreach(_.disabled = false)
	def disable(): Unit = inputs.foreach(_.disabled = true)

	inputBlock.onchange = _ => notifyListener()

	def reset(): Unit = selectedInput.foreach(_.checked = false)
	def notifyListener(): Unit = value.foreach(cb)

	private def selectedInput: Option[html.Input] = querySelector[html.Input](inputBlock, "input[type=radio]:checked")
}
