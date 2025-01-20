package se.lu.nateko.cp.meta.upload.formcomponents

import org.scalajs.dom.{html, document}
import se.lu.nateko.cp.meta.upload.Utils.*

class Select[T](elemId: String, labeller: T => String, titleMaker: T => String, autoselect: Boolean = false, cb: () => Unit = () => ()){
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
	def reset(): Unit = select.selectedIndex = -1

	def getOptions: IndexedSeq[T] = _values
	def setOptions(values: IndexedSeq[T]): Unit = {
		select.innerHTML = ""
		_values = values

		values.foreach{value =>
			val opt = document.createElement("option")
			opt.appendChild(document.createTextNode(labeller(value)))
			opt.setAttribute("title", titleMaker(value))
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

	def disable() = select.disabled = true
	def enable() = select.disabled = false
}
