package se.lu.nateko.cp.meta.upload.subforms

import se.lu.nateko.cp.meta.upload.formcomponents.HtmlElements
import se.lu.nateko.cp.meta.upload.*
import eu.icoscp.envri.Envri

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

abstract class PanelSubform(selector: String)(implicit bus: PubSubBus) {
	protected val htmlElements = new HtmlElements(selector)
	protected def notifyUpdate(): Unit = bus.publish(FormInputUpdated)
	private var gotPeepsOrgs: Boolean = false


	def resetForm(): Unit
	def hide(): Unit = htmlElements.hide()
	def show(): Unit = htmlElements.show()

	def getPeopleAndOrganizations()(implicit envri: Envri) = if(!gotPeepsOrgs){
		val done = for(
			people <- Backend.getPeople;
			organizations <- Backend.getOrganizations
		)
		yield {
			bus.publish(GotAgentList(organizations.concat(people)))
			bus.publish(GotOrganizationList(organizations))
		}
		UploadApp.whenDone(done)(_ => ())
		gotPeepsOrgs = true
	}
}
