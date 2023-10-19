package se.lu.nateko.cp.meta.upload.subforms

import se.lu.nateko.cp.meta.upload.*
import se.lu.nateko.cp.meta.{UploadDto, StaticCollectionDto}

import formcomponents.*
import Utils.*

class CollectionPanel(implicit bus: PubSubBus) extends PanelSubform(".collection-section"){
	def title = collectionTitle.value.withErrorContext("Collection title")
	def description = collectionDescription.value
	def members = collectionMembers.value.withErrorContext("Collection members (list of object urls)")
	def documentation = collectionDoc.value

	private val collectionTitle = new TextInput("collectiontitle", notifyUpdate, "collection title")
	private val collectionDescription = new DescriptionInput("collectiondescription", notifyUpdate)
	private val collectionMembers = new NonEmptyUriListInput("collectionmembers", notifyUpdate)
	private val collectionDoc = new HashOptInput("colldoc", notifyUpdate)

	def resetForm(): Unit = {
		collectionTitle.reset()
		collectionDescription.reset()
		collectionMembers.reset()
		collectionDoc.reset()
	}

	bus.subscribe{
		case GotUploadDto(dto) => handleDto(dto)
	}

	private def handleDto(upDto: UploadDto): Unit = upDto match {
		case dto: StaticCollectionDto =>
			collectionTitle.value = dto.title
			collectionMembers.value = dto.members
			collectionDescription.value = dto.description
			collectionDoc.value = dto.documentation
			notifyUpdate()
			show()
		case _ =>
			hide()
	}

}
