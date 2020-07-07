package se.lu.nateko.cp.meta.upload.subforms

import scala.util.{Try, Success, Failure}

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.SubmitterProfile
import se.lu.nateko.cp.meta.upload._
import se.lu.nateko.cp.meta.{UploadDto, DataObjectDto, DocObjectDto, StaticCollectionDto}

import formcomponents._
import ItemTypeRadio.{ItemType, Collection, Data, Document}
import UploadApp.whenDone
import Utils._

class CollectionPanel(implicit bus: PubSubBus) extends PanelSubform(".collection-section"){
	def title = collectionTitle.value.withErrorContext("Collection title")
	def description = collectionDescription.value
	def members = collectionMembers.value.withErrorContext("Collection members (list of object urls)")

	private val collectionTitle = new TextInput("collectiontitle", notifyUpdate, "collection title")
	private val collectionDescription = new TextOptInput("collectiondescription", notifyUpdate)
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
