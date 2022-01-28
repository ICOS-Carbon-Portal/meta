package se.lu.nateko.cp.meta.upload.subforms


import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.util.{Try, Success}

import se.lu.nateko.cp.meta.upload._
import se.lu.nateko.cp.meta.{UploadDto, DataObjectDto, DataProductionDto}
import se.lu.nateko.cp.meta.upload.formcomponents.HtmlElements

import formcomponents._
import Utils._
import se.lu.nateko.cp.meta.core.data.Envri


class ProductionPanel(implicit bus: PubSubBus, envri: Envri.Envri) extends PanelSubform(".production-section"){

	def dataProductionDtoOpt: Try[Option[DataProductionDto]] =
		if(!productionSwitch.checked) Success(None) else dataProductionDto.map(Some.apply)

	def dataProductionDto: Try[DataProductionDto] =
		 for(
			creator <- creatorInput.value.withErrorContext("Creator");
			contributors <- contributorsInput.values.withErrorContext("Contributors");
			hostOrganization = hostOrganizationInput.value.toOption;
			comment <- commentInput.value.withErrorContext("Comment");
			creationDate <- creationDateInput.value.withErrorContext("Creation date");
			sources <- sourcesInput.value.withErrorContext("Sources")
		) yield DataProductionDto(
			creator = creator.uri,
			contributors = contributors.map(_.uri),
			hostOrganization = hostOrganization.map(_.uri),
			comment = comment,
			sources = sources,
			creationDate = creationDate
		)

	private val productionSwitch = new Checkbox("production-switch", onProductionSwitched)
	private val productionCover =  new HtmlElements("#production-cover")

	private val agentList = new DataList[NamedUri]("agent-list", _.name)
	private val creatorInput = new DataListInput("creatoruri", agentList, notifyUpdate)
	private val contributorsInput = new DataListForm("contributors", agentList, notifyUpdate)
	private val organizationList = new DataList[NamedUri]("organization-list", _.name)
	private val hostOrganizationInput = new DataListInput("hostorganisation", organizationList, notifyUpdate)
	private val commentInput = new TextOptInput("productioncomment", notifyUpdate)
	private val creationDateInput = new InstantInput("creationdate", notifyUpdate)
	private val sourcesInput = new HashOptListInput("sources", notifyUpdate)
	private val agentAgg = new AgentAggregator

	def resetForm(): Unit = {
		creatorInput.reset()
		contributorsInput.setValues(Seq())
		hostOrganizationInput.reset()
		commentInput.reset()
		creationDateInput.reset()
		sourcesInput.reset()
	}

	override def show(): Unit = {
		super.getPeopleAndOrganizations()
		super.show()
	}

	bus.subscribe{
		case GotUploadDto(upDto) => handleDto(upDto)
		case ObjSpecSelected(spec) => onLevelSelected(spec.dataLevel)
		case LevelSelected(level) => onLevelSelected(level)
		case GotStationsList(stations) => {
			agentAgg.stations = stations.map(_.namedUri)
			agentList.values = agentAgg.agents
			organizationList.values = agentAgg.orgs
		}
		case GotAgentList(agents) =>
			agentAgg.agents = agents
			agentList.values = agentAgg.agents
		case GotOrganizationList(orgs) =>
			agentAgg.orgs = orgs
			organizationList.values = agentAgg.orgs
	}

	private def onLevelSelected(level: Int): Unit = level match {
		case 0 =>
			productionSwitch.uncheck()
			hide()
		case 1 | 2 =>
			productionSwitch.uncheck()
			productionSwitch.enable()
			show()
		case 3 =>
			productionSwitch.check()
			productionSwitch.disable()
			show()
	}

	private def onProductionSwitched(checked: Boolean) = {
		if (checked) productionCover.hide() else productionCover.show()
		notifyUpdate()
	}

	private def handleDto(upDto: UploadDto): Unit = upDto match {
		case dto: DataObjectDto => dto.specificInfo
			.fold(
				l3 => Some(l3.production),
				_.production
			)
			.fold(resetForm()){production =>
				for(
					people <- Backend.getPeople;
					organizations <- Backend.getOrganizations
				)
				yield {
					agentList.values = organizations.concat(people)
					organizationList.values = organizations

					agentList.values.find(_.uri == production.creator).map(agent => {
						creatorInput.value = agent
					})
					contributorsInput.setValues(production.contributors.flatMap(agentUri => agentList.values.find(_.uri == agentUri)))
					organizationList.values.find(org => production.hostOrganization.contains(org.uri)).map(org => {
						hostOrganizationInput.value = org
					})
					commentInput.value = production.comment
					creationDateInput.value = production.creationDate
					sourcesInput.value = production.sources
					productionSwitch.check()
					super.show()
				}
			}
		case _ =>
			hide()
	}
}

private class AgentAggregator{
	var stations: IndexedSeq[NamedUri] = IndexedSeq.empty
	private[this] var _agents: IndexedSeq[NamedUri] = IndexedSeq.empty
	private[this] var _orgs: IndexedSeq[NamedUri] = IndexedSeq.empty

	def agents_=(agents: IndexedSeq[NamedUri]): Unit = _agents = agents
	def orgs_=(orgs: IndexedSeq[NamedUri]): Unit = _orgs = orgs

	def agents = _agents ++ stations
	def orgs = _orgs ++ stations
}
