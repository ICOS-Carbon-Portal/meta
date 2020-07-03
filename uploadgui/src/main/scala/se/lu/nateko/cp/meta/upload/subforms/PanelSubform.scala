package se.lu.nateko.cp.meta.upload.subforms

import se.lu.nateko.cp.meta.upload.formcomponents.HtmlElements
import se.lu.nateko.cp.meta.upload.PubSubBus
import se.lu.nateko.cp.meta.upload.FormInputUpdated

abstract class PanelSubform(selector: String)(implicit bus: PubSubBus) {
	protected val htmlElements = new HtmlElements(selector)
	protected def notifyUpdate(): Unit = bus.publish(FormInputUpdated)


	def resetForm(): Unit
	def hide(): Unit = htmlElements.hide()
	def show(): Unit = htmlElements.show()
}
