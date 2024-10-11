package se.lu.nateko.cp.meta.upload.subforms

import se.lu.nateko.cp.meta.DocObjectDto
import se.lu.nateko.cp.meta.UploadDto

import se.lu.nateko.cp.meta.upload.*

import eu.icoscp.envri.Envri
import java.net.URI
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.Try

import formcomponents.*
import Utils.*
import se.lu.nateko.cp.meta.upload.UploadApp.whenDone


class DocumentPanel(using bus: PubSubBus, envri: Envri) extends PanelSubform(".document-section"){
	def title = documentTitle.value
	def description = documentDescription.value
	def authors = documentAuthors.values
	def licence: Try[Option[URI]] = licenceUrl.value.withErrorContext("Document licence URL")

	private val documentTitle = new TextOptInput("document-title", notifyUpdate)
	private val documentDescription = new DescriptionInput("document-description", notifyUpdate)
	private val agentList = new DataList[NamedUri]("agent-list", _.name)
	private val documentAuthors = new DataListForm("document-authors", agentList, notifyUpdate)
	private val agentAgg = new AgentAggregator
	private val licenceUrl = new UriOptInput("doclicenceselect", notifyUpdate)

	def resetForm(): Unit = {
		documentTitle.reset()
		documentDescription.reset()
		documentAuthors.setValues(Seq())
		licenceUrl.value = None
	}

	override def show(): Unit = {
		whenDone(getPeopleAndOrganizations()) { (people, organizations) =>
			agentAgg.agents = organizations.concat(people)
			agentList.values = agentAgg.agents
		}
		super.show()
	}

	bus.subscribe{
		case GotUploadDto(dto) => handleDto(dto)
		case GotStationsList(stations) => {
			agentAgg.stations = stations.map(_.namedUri)
			agentList.values = agentAgg.agents
		}
	}

	private def handleDto(upDto: UploadDto): Unit = upDto match {
		case dto: DocObjectDto =>
			for(
				people <- Backend.getPeople;
				organizations <- Backend.getOrganizations
			)
			yield {
				agentList.values = organizations.concat(people)
				documentTitle.value = dto.title
				documentAuthors.setValues(dto.authors.flatMap(agentUri => agentList.values.find(_.uri == agentUri)))
				documentDescription.value = dto.description
				licenceUrl.value = dto.references.flatMap(_.licence)
				notifyUpdate()
				super.show()
			}
		case _ =>
			hide()
	}

}
