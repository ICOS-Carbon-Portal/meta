package se.lu.nateko.cp.meta.upload.subforms

import scala.concurrent.Future
import scala.util.{Try, Success, Failure}

import org.scalajs.dom
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import se.lu.nateko.cp.doi.Doi

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.core.data.flattenToSeq
import se.lu.nateko.cp.meta.SubmitterProfile
import se.lu.nateko.cp.meta.upload.*
import se.lu.nateko.cp.meta.{DataObjectDto, DocObjectDto, StaticCollectionDto}

import formcomponents.*
import ItemTypeRadio.ItemType
import ItemType.{Collection, Data, Document}
import UploadApp.whenDone
import Utils.*
import java.net.URI
import se.lu.nateko.cp.meta.upload.formcomponents.ModeRadio.*


class AboutPanel(subms: IndexedSeq[SubmitterProfile])(using bus: PubSubBus, envri: Envri) extends PanelSubform("about-section"):

	def submitterOpt: Option[SubmitterProfile] = submitterIdSelect.value
	def submitter: Try[SubmitterProfile] = submitterOpt.withMissingError("Submitter Id not set")
	def isNewItemOrVersion: Boolean = modeControl.isNewItemOrVersion
	def itemType: Option[ItemType] = typeControl.value
	def file: Try[dom.File] = fileInput.file
	def itemName: Try[String] = if(isNewItemOrVersion) fileInput.file.map(_.name) else fileNameInput.value;
	def itemHash: Try[Sha256Sum] = if(isNewItemOrVersion) fileInput.hash else Success(fileHash.get)
	def previousVersion: Try[OptionalOneOrSeq[Sha256Sum]] = previousVersionInput.value
		.flatMap(validateNextVersion).withErrorContext("Previous version")
	def existingDoi: Try[Option[Doi]] = existingDoiInput.value.withErrorContext("Pre-existing DOI")
	def metadataUri: Try[URI] = metadataUriInput.value
	def duplicateFilenameAllowed: Option[Boolean] = Some(duplicateFilenameAllowedInput.checked)
	def autodeprecateSameFilenameObjects: Option[Boolean] = Some(autoDeprecateInput.checked)
	def partialUpload: Option[Boolean] = Some(partialUploadCheckbox.checked)

	def refreshFileHash(): Future[Unit] = if (fileInput.hasBeenModified) fileInput.rehash() else Future.successful(())

	private val modeControl = new ModeRadio("new-update-radio", onModeSelected)
	private val submitterIdSelect = new Select[SubmitterProfile]("submitteridselect", _.id, autoselect = true, onSubmitterSelected)
	private val typeControl = new ItemTypeRadio("file-type-radio", onItemTypeSelected)
	private val fileElement = new HtmlElements("#file-element")
	private val fileNameElement = new HtmlElements("#filename-element")
	private val fileInput = new FileInput("fileinput", notifyUpdate)
	private val fileNameInput = new TextInput("filename", notifyUpdate, "file name")
	private val fileOptionsElement = new HtmlElements("#fileoptions-element")
	private val duplicateFilenameAllowedElement = new HtmlElements("#duplicatefile-checkbox-elem")
	private val duplicateFilenameAllowedInput = new Checkbox("duplicatefile-checkbox", _ => notifyUpdate())
	private val autoDeprecateInput = new Checkbox("autodeprecate-checkbox", c => {onAutoDeprecateSelected(c); notifyUpdate()})
	private val partialUploadElement = new HtmlElements("#partialupload-checkbox-elem")
	private val partialUploadCheckbox = Checkbox("partialupload-checkbox", c =>{onPartialUploadSelected(c); notifyUpdate()})
	private val previousVersionInput = HashOptOneOrManyInput("previoushash", notifyUpdate)
	private val previousVersionDescr = Text("previoushash-descr")
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
		fileOptionsElement.hide()
		metadataUrlElement.hide()
		clearFields()
		partialUploadElement.hide()
	}

	private def clearFields(): Unit = {
		metadataUriInput.reset()
		fileNameInput.reset()
		previousVersionInput.reset()
		existingDoiInput.reset()
		updateGetMetadataButton()
		partialUploadElement.hide()
	}

	private def onModeSelected(mode: Mode): Unit = {
		UploadApp.hideAlert()
		mode match {
			case NewItem =>
				fileNameElement.hide()
				metadataUrlElement.hide()
				typeControl.enable()
				typeControl.reset()
				fileOptionsElement.hide()
				setPreviousVersionEditability(mode)
				partialUploadElement.hide()
			case Update =>
				fileElement.hide()
				fileNameElement.show()
				metadataUrlElement.show()
				typeControl.reset()
				typeControl.disable()
				metadataUriInput.focus()
				fileOptionsElement.show()
				setPreviousVersionEditability(mode)
				partialUploadElement.hide()
			case NewVersion =>
				fileNameElement.hide()
				metadataUrlElement.show()
				typeControl.reset()
				typeControl.disable()
				fileOptionsElement.hide()
				metadataUriInput.focus()
				partialUploadElement.hide()
			}
		bus.publish(ModeChanged)
		clearFields()
	}

	private def onItemTypeSelected(itemType: ItemType): Unit = {
		setFileAndFilenameVisibility(itemType)
		bus.publish(ItemTypeSelected(itemType))
	}

	private def onAutoDeprecateSelected(checked: Boolean): Unit = {
		if checked then
			duplicateFilenameAllowedInput.uncheck()
			duplicateFilenameAllowedInput.disable()
			duplicateFilenameAllowedElement.disable()
		else
			duplicateFilenameAllowedInput.enable()
			duplicateFilenameAllowedElement.enable()
	}

	private def onPartialUploadSelected(checked: Boolean): Unit =
		if checked then
			previousVersionDescr.setText("Previous version (one hex or base64 hashsum)")
		else
			previousVersionDescr.setText("Previous versions (one hex or base64 hashsum per line)")

	private def setFileAndFilenameVisibility(itemType: ItemType): Unit = itemType match {
		case Collection =>
			fileElement.hide()
			fileNameElement.hide()
			fileOptionsElement.hide()
			partialUploadElement.hide()
		case _ =>
			fileOptionsElement.show()
			partialUploadElement.show()
			if(isNewItemOrVersion) {
				fileElement.show()
				fileNameElement.hide()
			} else {
				fileElement.hide()
				fileNameElement.show()
			}
	}

	private def setPreviousVersionEditability(mode: Mode): Unit =
		mode match {
			case NewVersion => previousVersionInput.disable()
			case _ => previousVersionInput.enable()
		}

	private def onSubmitterSelected(): Unit = submitterIdSelect.value.foreach{subm =>
		bus.publish(GotStationsList(IndexedSeq.empty))
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
					previousVersionInput.value =
						if (isNewItemOrVersion) Some(Left(dto.hashSum))
						else dto.isNextVersionOf
					existingDoiInput.value = dto.preExistingDoi
				}
				case dto: DocObjectDto =>
					typeControl.value = Document
					fileNameInput.value = dto.fileName
					fileHash = Some(dto.hashSum)
					previousVersionInput.value =
						if (isNewItemOrVersion) Some(Left(dto.hashSum))
						else dto.isNextVersionOf
					existingDoiInput.value = dto.preExistingDoi
				case dto: StaticCollectionDto =>
					typeControl.value = Collection
					fileNameInput.value = ""
					fileHash = None
					previousVersionInput.value =
						if (isNewItemOrVersion) Sha256Sum.fromString(metadataUri.getPath().split("/").last).toOption.map(Left(_))
						else dto.isNextVersionOf
					existingDoiInput.value = dto.preExistingDoi
			}.foreach{dto =>
				typeControl.value.foreach(setFileAndFilenameVisibility)
				modeControl.value.foreach(setPreviousVersionEditability)
				bus.publish(GotUploadDto(dto))
			}
		}
	}

	private def validateNextVersion(next: OptionalOneOrSeq[Sha256Sum]): Try[OptionalOneOrSeq[Sha256Sum]] =
		if partialUploadCheckbox.checked then
			val amount = next.flattenToSeq.size
			if amount == 1 then Success(next)
			else fail(s"Partial upload requires exactly one previous version, but $amount were given")
		else Success(next)

end AboutPanel
