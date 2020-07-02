package se.lu.nateko.cp.meta.upload.subforms

import scala.util.{Try, Success}

import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.upload._
import se.lu.nateko.cp.meta.{UploadDto, DataObjectDto, DataProductionDto}

import formcomponents._
import ItemTypeRadio.{ItemType, Collection, Data, Document}
import UploadApp.whenDone
import Utils._


class ProductionPanel(implicit bus: PubSubBus, envri: Envri.Envri) {

	def dataProductionDto: Try[Option[DataProductionDto]] =
		if(!htmlElements.areEnabled) Success(None) else for(
			creator <- creatorInput.value.withErrorContext("Creator");
			contributors <- contributorsInput.value.withErrorContext("Contributors");
			hostOrganization <- hostOrganizationInput.value.withErrorContext("Host organization");
			comment <- commentInput.value.withErrorContext("Comment");
			creationDate <- creationDateInput.value.withErrorContext("Creation date");
			sources <- sourcesInput.value.withErrorContext("Sources")
		) yield Some(DataProductionDto(
			creator = creator,
			contributors = contributors,
			hostOrganization = hostOrganization,
			comment = comment,
			sources = sources,
			creationDate = creationDate
		))

	private val htmlElements = new HtmlElements(".production-section")
	private val creatorInput = new UriInput("creatoruri", notifyUpdate)
	private val contributorsInput = new UriListInput("contributors", notifyUpdate)
	private val hostOrganizationInput = new UriOptInput("hostorganisation", notifyUpdate)
	private val commentInput = new TextOptInput("productioncomment", notifyUpdate)
	private val creationDateInput = new InstantInput("creationdate", notifyUpdate)
	private val sourcesInput = new HashOptListInput("sources", notifyUpdate)

	def resetForm(): Unit = {
		creatorInput.reset()
		contributorsInput.reset()
		hostOrganizationInput.reset()
		commentInput.reset()
		creationDateInput.reset()
		sourcesInput.reset()
	}

	bus.subscribe{
		case GotUploadDto(upDto) => handleDto(upDto)
		case ItemTypeSelected(Data) => resetForm()
		case ItemTypeSelected(_) => htmlElements.hide()
		case ObjSpecSelected(spec) if spec.dataLevel == 0 => htmlElements.hide()
	}

	private def notifyUpdate(): Unit = bus.publish(FormInputUpdated)

	private def handleDto(upDto: UploadDto): Unit = upDto match {
		case dto: DataObjectDto => dto.specificInfo
			.fold(
				l3 => Some(l3.production),
				_.production
			)
			.fold(resetForm()){production =>
				creatorInput.value = production.creator
				contributorsInput.value = production.contributors
				hostOrganizationInput.value = production.hostOrganization
				commentInput.value = production.comment
				creationDateInput.value = production.creationDate
				sourcesInput.value = production.sources
				htmlElements.show()
			}
		case _ =>
			htmlElements.hide()
	}
}
