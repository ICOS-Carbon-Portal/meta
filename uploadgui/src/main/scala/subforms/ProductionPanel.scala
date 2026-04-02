package se.lu.nateko.cp.meta.upload.subforms

import scala.util.{Try, Success}

import se.lu.nateko.cp.meta.upload.*
import se.lu.nateko.cp.meta.{UploadDto, DataObjectDto, DataProductionDto}

import formcomponents.*
import Utils.*
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.upload.UploadApp.whenDone


class ProductionPanel(using bus: PubSubBus, envri: Envri) extends PanelSubform(".production-section"){

	def dataProductionDtoOpt: Try[Option[DataProductionDto]] =
		if(!productionSwitch.checked) Success(None) else dataProductionDto.map(Some.apply)

	def dataProductionDto: Try[DataProductionDto] =
		 for(
			creator <- creatorInput.value.withErrorContext("Creator");
			contributors <- contributorsInput.values.withErrorContext("Contributors");
			hostOrganization = hostOrganizationInput.value.toOption;
			comment <- commentInput.value.withErrorContext("Comment");
			creationDate <- creationDateInput.value.withErrorContext("Creation date");
			sources <- sourcesInput.value.withErrorContext("Sources");
			doc <- docInput.value
		) yield DataProductionDto(
			creator = creator.uri,
			contributors = contributors.map(_.uri),
			hostOrganization = hostOrganization.map(_.uri),
			comment = comment,
			sources = sources,
			creationDate = creationDate,
			documentation = doc
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
	private val docInput = new HashOptInput("proddoc", notifyUpdate)
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
		whenDone(getPeopleAndOrganizations())(updateAgentsAndOrgs)
		super.show()
	}

	bus.subscribe{
		case GotUploadDto(upDto) => handleDto(upDto)
		case ObjSpecSelected(spec) => onSpecSelected(spec)
		case LevelSelected(_) => hide()
		case GotStationsList(stations) => {
			agentAgg.stations = stations.map(_.namedUri)
			agentList.values = agentAgg.agents
			organizationList.values = agentAgg.orgs
		}
}

	private def onSpecSelected(spec: ObjSpec): Unit = spec.dataLevel match {
		case 0 =>
			if(creatorInput.isEmpty) productionSwitch.uncheck()
			hide()
		case 1 | 2 =>
			if(creatorInput.isEmpty) productionSwitch.uncheck()
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

	private def updateAgentsAndOrgs(people: IndexedSeq[NamedUri], organizations: IndexedSeq[NamedUri]) = {
		agentAgg.agents = organizations.concat(people)
		agentList.values = agentAgg.agents
		agentAgg.orgs = organizations
		organizationList.values = agentAgg.orgs
	}

	private def handleDto(upDto: UploadDto): Unit = upDto match {
		case dto: DataObjectDto => dto.specificInfo
			.fold(
				l3 => Some(l3.production),
				_.production
			)
			.fold(resetForm()){production =>
				whenDone(getPeopleAndOrganizations()) { (people, organizations) =>
					updateAgentsAndOrgs(people, organizations)

					agentList.values.find(_.uri == production.creator).map(agent => {
						creatorInput.value = agent
					})

					contributorsInput.setValues(production.contributors.flatMap(agentUri => agentAgg.agents.find(_.uri == agentUri)))
					organizationList.values.find(org => production.hostOrganization.contains(org.uri)).map(org => {
						hostOrganizationInput.value = org
					})
					commentInput.value = production.comment
					creationDateInput.value = production.creationDate
					sourcesInput.value = production.sources
					docInput.value = production.documentation
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
	private var _agents: IndexedSeq[NamedUri] = IndexedSeq.empty
	private var _orgs: IndexedSeq[NamedUri] = IndexedSeq.empty

	def agents_=(agents: IndexedSeq[NamedUri]): Unit = _agents = agents
	def orgs_=(orgs: IndexedSeq[NamedUri]): Unit = _orgs = orgs

	def agents = _agents ++ stations
	def orgs = _orgs ++ stations
}
