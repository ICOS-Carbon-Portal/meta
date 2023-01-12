package se.lu.nateko.cp.meta.upload.subforms

import se.lu.nateko.cp.meta.upload.*
import se.lu.nateko.cp.meta.{UploadDto, StaticCollectionDto}

import formcomponents.*
import Utils.*

class CollectionPanel(implicit bus: PubSubBus) extends PanelSubform(".collection-section"){
	def title = collectionTitle.value.withErrorContext("Collection title")
	def description = collectionDescription.value
	def members = collectionMembers.value.withErrorContext("Collection members (list of object urls)")

	private val collectionTitle = new TextInput("collectiontitle", notifyUpdate, "collection title")
	private val collectionDescription = new DescriptionInput("collectiondescription", notifyUpdate)
	private val collectionMembers = new NonEmptyUriListInput("collectionmembers", notifyUpdate)

	def resetForm(): Unit = {
		collectionTitle.reset()
		collectionDescription.reset()
		collectionMembers.reset()
	}

	bus.subscribe{
		case GotUploadDto(dto) => handleDto(dto)
	}

	private def handleDto(upDto: UploadDto): Unit = upDto match {
		case dto: StaticCollectionDto =>
			collectionTitle.value = dto.title
			collectionMembers.value = dto.members
			collectionDescription.value = dto.description
			notifyUpdate()
			show()
		case _ =>
			hide()
	}

}
