package se.lu.nateko.cp.meta.upload.subforms

import se.lu.nateko.cp.meta.upload.*
import se.lu.nateko.cp.meta.{UploadDto, StaticCollectionDto}

import formcomponents.*
import Utils.*

class CollectionPanel(covs: IndexedSeq[SpatialCoverage])(implicit bus: PubSubBus) extends PanelSubform(".collection-section"):
	def title = collectionTitle.value.withErrorContext("Collection title")
	def description = collectionDescription.value
	def members = collectionMembers.value.withErrorContext("Collection members (list of object urls)")
	def documentation = collectionDoc.value
	def coverage = spatialCovSelect.spatialCoverage

	private val collectionTitle = new TextInput("collectiontitle", notifyUpdate, "collection title")
	private val collectionDescription = new DescriptionInput("collectiondescription", notifyUpdate)
	private val collectionMembers = new NonEmptyUriListInput("collectionmembers", notifyUpdate)
	private val collectionDoc = new HashOptInput("colldoc", notifyUpdate)
	private val spatialCovSelect = new GeoCoverageSelector(covs, "collspatcover")

	def resetForm(): Unit =
		collectionTitle.reset()
		collectionDescription.reset()
		collectionMembers.reset()
		collectionDoc.reset()
		spatialCovSelect.resetForm()

	bus.subscribe:
		case GotUploadDto(dto) => handleDto(dto)

	private def handleDto(upDto: UploadDto): Unit = upDto match
		case dto: StaticCollectionDto =>
			collectionTitle.value = dto.title
			collectionMembers.value = dto.members
			collectionDescription.value = dto.description
			collectionDoc.value = dto.documentation
			dto.coverage.foreach(spatialCovSelect.handleReceivedSpatialCoverage)
			notifyUpdate()
			show()
		case _ =>
			hide()

end CollectionPanel