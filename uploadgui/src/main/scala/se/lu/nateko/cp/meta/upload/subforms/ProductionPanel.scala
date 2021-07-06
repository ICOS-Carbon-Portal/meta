package se.lu.nateko.cp.meta.upload.subforms


import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import scala.util.{Try, Success}

import se.lu.nateko.cp.meta.upload._
import se.lu.nateko.cp.meta.{UploadDto, DataObjectDto, DataProductionDto}

import formcomponents._
import Utils._
import se.lu.nateko.cp.meta.core.data.Envri


class ProductionPanel(implicit bus: PubSubBus, envri: Envri.Envri) extends PanelSubform(".production-section"){

	def dataProductionDtoOpt: Try[Option[DataProductionDto]] =
		if(!htmlElements.areEnabled) Success(None) else dataProductionDto.map(Some.apply)

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

	private val agentList = new DataList[NamedUri]("agent-list", _.name)
	private val creatorInput = new DataListInput("creatoruri", agentList, notifyUpdate)
	private val contributorsInput = new DataListForm("contributors", agentList, notifyUpdate)
	private val organizationList = new DataList[NamedUri]("organization-list", _.name)
	private val hostOrganizationInput = new DataListInput("hostorganisation", organizationList, notifyUpdate)
	private val commentInput = new TextOptInput("productioncomment", notifyUpdate)
	private val creationDateInput = new InstantInput("creationdate", notifyUpdate)
	private val sourcesInput = new HashOptListInput("sources", notifyUpdate)

	def resetForm(): Unit = {
		creatorInput.reset()
		contributorsInput.setValues(Seq())
		hostOrganizationInput.reset()
		commentInput.reset()
		creationDateInput.reset()
		sourcesInput.reset()
	}

	override def show(): Unit = {
		super.show()
		for(
			people <- Backend.getPeople;
			organizations <- Backend.getOrganizations
		)
		yield {
			bus.publish(GotAgentList(organizations.concat(people)))
			bus.publish(GotOrganizationList(organizations))
		}
	}

	bus.subscribe{
		case GotUploadDto(upDto) => handleDto(upDto)
		case ObjSpecSelected(spec) => onLevelSelected(spec.dataLevel)
		case LevelSelected(level) => onLevelSelected(level)
		case GotAgentList(agents) => agentList.values = agents
		case GotOrganizationList(orgs) => organizationList.values = orgs
	}

	private def onLevelSelected(level: Int): Unit = if(level == 0) hide() else if(level == 3) show()

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
					show()
				}
			}
		case _ =>
			hide()
	}
}
