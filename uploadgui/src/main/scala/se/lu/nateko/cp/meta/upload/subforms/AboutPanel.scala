package se.lu.nateko.cp.meta.upload.subforms

import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

import org.scalajs.dom
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import se.lu.nateko.cp.doi.Doi

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


class AboutPanel(subms: IndexedSeq[SubmitterProfile])(implicit bus: PubSubBus, envri: Envri.Envri) extends PanelSubform("about-section"){

	def submitter = submitterIdSelect.value.withMissingError("Submitter Id not set")
	def isInNewItemMode: Boolean = modeControl.value.contains("new")
	def itemType: Option[ItemType] = typeControl.value
	def file: Try[dom.File] = fileInput.file
	def itemName: Try[String] = if(isInNewItemMode) fileInput.file.map(_.name) else fileNameInput.value;
	def itemHash: Try[Sha256Sum] = if(isInNewItemMode) fileInput.hash else Success(fileHash.get)
	def previousVersion: Try[OptionalOneOrSeq[Sha256Sum]] = previousVersionInput.value.withErrorContext("Previous version")
	def existingDoi: Try[Option[Doi]] = existingDoiInput.value.withErrorContext("Pre-existing DOI")

	def refreshFileHash(): Future[Unit] = if (fileInput.hasBeenModified) fileInput.rehash() else Future.successful(())

	def documentObjectDto: Try[DocObjectDto] = for(
		submitter <- this.submitter;
		file <- itemName;
		hash <- itemHash;
		previousVersion <- this.previousVersion;
		doi <- existingDoi
	) yield DocObjectDto(
		hashSum = hash,
		submitterId = submitter.id,
		fileName = file,
		isNextVersionOf = previousVersion,
		preExistingDoi = doi
	)

	private val modeControl = new Radio[String]("new-update-radio", onModeSelected, s => Some(s), s => s)
	private val submitterIdSelect = new Select[SubmitterProfile]("submitteridselect", _.id, autoselect = true, onSubmitterSelected)
	private val typeControl = new ItemTypeRadio("file-type-radio", onItemTypeSelected)
	private val fileElement = new HtmlElements("#file-element")
	private val fileNameElement = new HtmlElements("#filename-element")
	private val fileInput = new FileInput("fileinput", notifyUpdate)
	private val fileNameInput = new TextInput("filename", notifyUpdate)
	private val previousVersionInput = new HashOptInput("previoushash", notifyUpdate)
	private val existingDoiInput = new DoiOptInput("existingdoi", notifyUpdate)
	private val metadataUrlElement = new HtmlElements("#metadata-url")
	private val metadataUriInput = new UriInput("metadata-update", updateGetMetadataButton)
	private val getMetadataButton = new Button("get-metadata", getMetadata)

	private var fileHash: Option[Sha256Sum] = None

	submitterIdSelect.setOptions(subms)
	resetForm()

	def resetForm(): Unit = {
		modeControl.reset()
		modeControl.disable()
		typeControl.reset()
		typeControl.disable()
		getMetadataButton.disable("Missing landing page URL")
		fileNameElement.hide()
		fileElement.hide()
		metadataUrlElement.hide()
		clearFields()
	}

	private def clearFields(): Unit = {
		metadataUriInput.reset()
		fileNameInput.reset()
		previousVersionInput.reset()
		existingDoiInput.reset()
		updateGetMetadataButton()
	}

	private def onModeSelected(modeName: String): Unit = {
		if(isInNewItemMode){
			fileNameElement.hide()
			metadataUrlElement.hide()
			typeControl.enable()
			typeControl.reset()
		} else {
			fileElement.hide()
			fileNameElement.show()
			metadataUrlElement.show()
			typeControl.reset()
			typeControl.disable()
		}
		bus.publish(ModeChanged)
		clearFields()
	}

	
	private def onItemTypeSelected(itemType: ItemType): Unit = {
		itemType match {
			case Collection =>
				fileElement.hide()
				fileNameElement.hide()
			case _ =>
				fileElement.show()
				fileNameElement.hide()
		}
		bus.publish(ItemTypeSelected(itemType))
	}

	private def onSubmitterSelected(): Unit = submitterIdSelect.value.foreach{subm =>
		bus.publish(GotStationsList(IndexedSeq.empty))
		bus.publish(ModeChanged)
		resetForm()
		updateGetMetadataButton()
		whenDone(Backend.stationInfo(subm.producingOrganizationClass, subm.producingOrganization)){
			stations =>
				bus.publish(GotStationsList(stations))
				modeControl.enable()
		}
	}

	private def updateGetMetadataButton(): Unit = {
		val ok = for(
			_ <- submitterIdSelect.value.withMissingError("Submitter Id not set");
			_ <- metadataUriInput.value
		) yield ()

		ok match {
			case Success(_) => getMetadataButton.enable()
			case Failure(err) => getMetadataButton.disable(err.getMessage)
		}
	}

	private def getMetadata(): Unit = {
		UploadApp.hideAlert()
		metadataUriInput.value.foreach { metadataUri =>
			whenDone(Backend.getMetadata(metadataUri)) {
				case dto: DataObjectDto => {
					typeControl.value = Data
					fileNameInput.value = dto.fileName
					fileHash = Some(dto.hashSum)
					previousVersionInput.value = dto.isNextVersionOf
					existingDoiInput.value = dto.preExistingDoi
				}
				case dto: DocObjectDto =>
					typeControl.value = Document
					fileNameInput.value = dto.fileName
					fileHash = Some(dto.hashSum)
					previousVersionInput.value = dto.isNextVersionOf
					existingDoiInput.value = dto.preExistingDoi
				case dto: StaticCollectionDto =>
					typeControl.value = Collection
					fileNameInput.value = ""
					fileHash = None
					previousVersionInput.value = dto.isNextVersionOf
					existingDoiInput.value = dto.preExistingDoi
				case _ =>
					UploadApp.showAlert(s"$metadataUri cannot be found", "alert alert-danger")
			}.foreach{dto =>
				bus.publish(GotUploadDto(dto))
			}
		}
	}

}
