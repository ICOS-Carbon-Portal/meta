package se.lu.nateko.cp.meta.upload

import org.scalajs.dom
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.*

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}
import Utils.*
import se.lu.nateko.cp.meta.upload.formcomponents.*
import ItemTypeRadio.ItemType.{Collection, Data, Document}
import UploadApp.{hideAlert, whenDone, progressBar}
import se.lu.nateko.cp.meta.upload.subforms.*
import se.lu.nateko.cp.meta.{SpatioTemporalDto, StationTimeSeriesDto}
import java.net.URI

class Form(
	subms: IndexedSeq[SubmitterProfile],
	objSpecs: IndexedSeq[ObjSpec],
	spatCovs: IndexedSeq[SpatialCoverage],
	keyWords: IndexedSeq[String],
	onUpload: (UploadDto, Option[dom.File]) => Unit,
	createDoi: URI => Unit
)(using envri: Envri, envriConf: EnvriConfig, bus: PubSubBus) {

	val aboutPanel = new AboutPanel(subms)
	val dataPanel = new DataPanel(objSpecs, keyWords, () => aboutPanel.submitterOpt)
	val statTsPanel = new StationTimeSeriesPanel
	val prodPanel = new ProductionPanel
	val collPanel = new CollectionPanel
	val docPanel = new DocumentPanel
	val spatTempPanel = new SpatioTemporalPanel(spatCovs)
	val submitButton = new Button("submitbutton", submitAction)

	bus.subscribe{
		case GotUploadDto(_) => handleDto()
		case FormInputUpdated => updateButton()
		case ModeChanged => resetForm()
		case ItemTypeSelected(itemType) =>
			resetForm()
			itemType match{
				case Data => dataPanel.show()
				case Collection => collPanel.show()
				case Document => docPanel.show()
			}
	}

	def resetForm(): Unit = {
		updateButton()
		subforms.foreach{sf =>
			sf.resetForm()
			sf.hide()
		}
	}

	def subforms = Seq(dataPanel, statTsPanel, prodPanel, collPanel, docPanel, spatTempPanel)

	def submitAction(): Unit = {
		dom.window.scrollTo(0, 0)
		updateButton()
		hideAlert()
		progressBar.show()
		aboutPanel.itemType match {
			case Some(Data) =>
				if(aboutPanel.isNewItemOrVersion) {
					whenDone {
						aboutPanel.refreshFileHash()
					}{ _ =>
						for(
							dto <- dataObjectDto;
							file <- aboutPanel.file;
							nRows <- dataPanel.nRows;
							varnames <- spatTempPanel.varnames;
							spec <- dataPanel.objSpec
						) {
							whenDone(Backend.tryIngestion(file, spec, nRows, varnames)){ _ =>
								onUpload(dto, Some(file))
							}.failed.foreach {
								case _ => progressBar.hide()
							}
						}
					}
				} else
					dataObjectDto.foreach(onUpload(_, None))

			case Some(Collection) =>
				staticCollectionDto.foreach(onUpload(_, None))

			case Some(Document) =>
				for(
					dto <- documentObjectDto;
					fileOpt <- if(aboutPanel.isNewItemOrVersion) aboutPanel.file.map(Some.apply) else Success(None)
				) onUpload(dto, fileOpt)

			case _ =>
		}
	}

	private def updateButton(): Unit = dto match {
		case Success(_) => submitButton.enable()
		case Failure(err) => submitButton.disable(err.getMessage)
	}

	def dto: Try[UploadDto] = aboutPanel.itemType match {
		case Some(Data) => dataObjectDto
		case Some(Collection) => staticCollectionDto
		case Some(Document) => documentObjectDto
		case _ => fail("No file type selected")
	}

	def dataObjectDto: Try[DataObjectDto] = for(
		submitter <- aboutPanel.submitter;
		file <- aboutPanel.itemName;
		hash <- aboutPanel.itemHash;
		previousVersion <- aboutPanel.previousVersion;
		doi <- aboutPanel.existingDoi;
		objSpec <- dataPanel.objSpec;
		keywords <- dataPanel.keywords;
		licence <- dataPanel.licence;
		moratorium <- dataPanel.moratorium;
		specInfo <- specificInfo
	) yield DataObjectDto(
		hashSum = hash,
		submitterId = submitter.id,
		objectSpecification = objSpec.uri,
		fileName = file,
		specificInfo = specInfo,
		isNextVersionOf = previousVersion,
		preExistingDoi = doi,
		references = Some(ReferencesDto(
			keywords = Option(keywords.map(_.trim).filter(!_.isEmpty)).filter(!_.isEmpty),
			licence = licence,
			moratorium = moratorium,
			duplicateFilenameAllowed = aboutPanel.duplicateFilenameAllowed,
			autodeprecateSameFilenameObjects = aboutPanel.autodeprecateSameFilenameObjects,
			partialUpload = aboutPanel.partialUpload
		))
	)

	def specificInfo: Try[Either[SpatioTemporalDto, StationTimeSeriesDto]] = dataPanel.objSpec.flatMap{spec =>
		if(spec.isSpatiotemporal)
			spatTempPanel.meta(prodPanel.dataProductionDto).map(Left.apply)
		else for(
			station <- statTsPanel.station;
			acqInterval <- statTsPanel.timeInterval;
			nRows <- dataPanel.nRows;
			samplingPoint <- statTsPanel.samplingPoint;
			samplingHeight <- statTsPanel.samplingHeight;
			instrumentUri <- statTsPanel.instrUri;
			production <- prodPanel.dataProductionDtoOpt
		) yield Right(
			StationTimeSeriesDto(
				station = station.namedUri.uri,
				site = statTsPanel.site.flatten.map(_.uri),
				instrument = instrumentUri,
				samplingPoint = samplingPoint,
				samplingHeight = samplingHeight,
				acquisitionInterval = acqInterval,
				nRows = nRows,
				production = production
			)
		)
	}

	def staticCollectionDto: Try[StaticCollectionDto] = for(
		title <- collPanel.title;
		description <- collPanel.description;
		members <- collPanel.members;
		previousVersion <- aboutPanel.previousVersion;
		doi <- aboutPanel.existingDoi;
		submitter <- aboutPanel.submitter
	) yield StaticCollectionDto(
		submitterId = submitter.id,
		members = members,
		title = title,
		description = description,
		isNextVersionOf = previousVersion,
		preExistingDoi = doi
	)

	def documentObjectDto: Try[DocObjectDto] = for(
		submitter <- aboutPanel.submitter;
		file <- aboutPanel.itemName;
		hash <- aboutPanel.itemHash;
		title <- docPanel.title;
		description <- docPanel.description;
		authors <- docPanel.authors;
		previousVersion <- aboutPanel.previousVersion;
		doi <- aboutPanel.existingDoi;
		licence <- docPanel.licence
	) yield DocObjectDto(
		hashSum = hash,
		submitterId = submitter.id,
		fileName = file,
		title = title,
		description = description,
		authors = authors.map(_.uri),
		isNextVersionOf = previousVersion,
		preExistingDoi = doi,
		references = Some(ReferencesDto(
			keywords = None,
			licence = licence,
			moratorium = None,
			duplicateFilenameAllowed = aboutPanel.duplicateFilenameAllowed,
			autodeprecateSameFilenameObjects = aboutPanel.autodeprecateSameFilenameObjects,
			partialUpload = aboutPanel.partialUpload
		))
	)

	private def handleDto(): Unit = {
		hideAlert()
		if(!aboutPanel.isNewItemOrVersion) {
			for(
				metaURL <- aboutPanel.metadataUri.toOption
			){
				val newDoiButton = new Button("new-doi-button", () => createDoi(metaURL))
				newDoiButton.enable()
			}
		}
	}
}
