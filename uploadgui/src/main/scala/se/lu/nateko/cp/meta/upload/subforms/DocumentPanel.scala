package se.lu.nateko.cp.meta.upload.subforms

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import se.lu.nateko.cp.meta.upload._
import se.lu.nateko.cp.meta.{UploadDto, DocObjectDto}
import se.lu.nateko.cp.meta.core.data.Envri

import formcomponents._

class DocumentPanel(implicit bus: PubSubBus, envri: Envri.Envri) extends PanelSubform(".document-section"){
	def title = documentTitle.value
	def description = documentDescription.value
	def authors = documentAuthors.values

	private val documentTitle = new TextOptInput("document-title", notifyUpdate)
	private val documentDescription = new TextOptInput("document-description", notifyUpdate)
	private val agentList = new DataList[NamedUri]("agent-list", _.name)
	private val documentAuthors = new DataListForm("document-authors", agentList, notifyUpdate)
	private val agentAgg = new AgentAggregator

	def resetForm(): Unit = {
		documentTitle.reset()
		documentDescription.reset()
		documentAuthors.setValues(Seq())
	}

	override def show(): Unit = {
		super.getPeopleAndOrganizations()
		super.show()
	}

	bus.subscribe{
		case GotUploadDto(dto) => handleDto(dto)
		case GotStationsList(stations) => {
			agentAgg.stations = stations.map(_.namedUri)
			agentList.values = agentAgg.agents
		}
		case GotAgentList(agents) =>
			agentAgg.agents = agents
			agentList.values = agentAgg.agents
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
				notifyUpdate()
				super.show()
			}
		case _ =>
			hide()
	}

}
