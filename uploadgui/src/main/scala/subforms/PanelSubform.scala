package se.lu.nateko.cp.meta.upload.subforms

import se.lu.nateko.cp.meta.upload.formcomponents.HtmlElements
import se.lu.nateko.cp.meta.upload.*
import eu.icoscp.envri.Envri

import scala.concurrent.Future
import java.net.URI

abstract class PanelSubform(selector: String)(using bus: PubSubBus) {
	protected val htmlElements = new HtmlElements(selector)
	protected def notifyUpdate(): Unit = bus.publish(FormInputUpdated)
	private type AgentList = IndexedSeq[NamedUri]
	private var peepsOrgsFut: Option[Future[(AgentList, AgentList)]] = None
	private var variablesFut: Option[Future[IndexedSeq[DatasetVar]]] = None


	def resetForm(): Unit
	def hide(): Unit = htmlElements.hide()
	def show(): Unit = htmlElements.show()

	protected def getPeopleAndOrganizations()(using Envri) = if peepsOrgsFut.isEmpty then
		val result = Backend.getPeople.zip(Backend.getOrganizations)
		peepsOrgsFut = Some(result)
		result
	else
		peepsOrgsFut.get

	protected def getVariables(dataset: URI) = if variablesFut.isEmpty then
		val result = Backend.getDatasetVariables(dataset)
		variablesFut = Some(result)
		result
	else
		variablesFut.get

}
