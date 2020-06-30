package se.lu.nateko.cp.meta.upload

import org.scalajs.dom
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.{StationDataMetadata, SubmitterProfile, DataObjectDto, DocObjectDto, UploadDto, StaticCollectionDto, DataProductionDto}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import Utils._
import se.lu.nateko.cp.meta.upload.formcomponents._
import ItemTypeRadio._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.net.URI
import UploadApp.{hideAlert, showAlert, whenDone, progressBar}
import se.lu.nateko.cp.meta.upload.subforms._

class Form(
	subms: IndexedSeq[SubmitterProfile],
	objSpecs: IndexedSeq[ObjSpec],
	onUpload: (UploadDto, Option[dom.File]) => Unit,
)(implicit envri: Envri.Envri, envriConf: EnvriConfig, bus: PubSubBus) {

	val aboutPanel = new AboutPanel(subms)
	val dataPanel = new DataPanel(objSpecs)
	val acqPanel = new AcquisitionPanel
	val formElement = new FormElement("form-block")
	val positionElements = new HtmlElements(".position-element")
	val customSamplingPoint = SamplingPoint(new URI(""), 0, 0, "Custom")

	def resetForm() = {
		formElement.reset()
		updateButton()
	}

	val dataElements = new HtmlElements(".data-section")
	val collectionElements = new HtmlElements(".collection-section")
	val productionElements = new HtmlElements(".production-section")
	val acquisitionSection = new HtmlElements(".acq-section")
	val l3Section = new HtmlElements(".l3-section")

	bus.subscribe{
		case GotUploadDto(dto) => handleDto(dto)
	}
	def submitAction(): Unit = {
		dom.window.scrollTo(0, 0)
		submitButton.disable("")
		hideAlert()
		progressBar.show()
		aboutPanel.itemType match {
			case Some(Data) =>
				if(aboutPanel.isInNewItemMode) {
					whenDone {
						aboutPanel.refreshFileHash()
					}{ _ =>
						for(dto <- dataObjectDto; file <- aboutPanel.file; nRows <- dataPanel.nRows; spec <- dataPanel.objSpec) {
							whenDone(Backend.tryIngestion(file, spec, nRows)){ _ =>
								onUpload(dto, Some(file))
							}.failed.foreach {
								case _ => progressBar.hide()
							}
						}
					}
				} else{
					for(dto <- dataObjectDto) {
						onUpload(dto, None)
					}
				}
			case Some(Collection) =>
				for(dto <- staticCollectionDto) {
					onUpload(dto, None)
				}
			case Some(Document) =>
				if(aboutPanel.isInNewItemMode) {
					for(dto <- documentObjectDto; file <- aboutPanel.file) {
						onUpload(dto, Some(file))
					}
				} else {
					for(dto <- documentObjectDto) {
						onUpload(dto, None)
					}
				}
			case _ =>
		}
	}
	val submitButton = new Button("submitbutton", () => submitAction())

	private def updateButton(): Unit = dto match {
		case Success(_) => submitButton.enable()
		case Failure(err) => submitButton.disable(err.getMessage)
	}


	val addProductionButton = new Button("addproductionbutton", () => {
		disableProductionButton()
		productionElements.show()
		updateButton()
	})

	val disableProductionButton: () => Unit = () => {
		addProductionButton.disable("")
	}

	val enableProductionButton: () => Unit = () => {
		addProductionButton.enable()
	}

	val removeProductionButton = new Button("removeproductionbutton", () => {
		enableProductionButton()
		productionElements.hide()
		updateButton()
	})

	private val onStationSelected: () => Unit = () => {
		stationSelect.value.foreach { station =>
			whenDone(Backend.getSites(station.uri)) { sites =>
				siteSelect.setOptions {
					if (sites.isEmpty) IndexedSeq.empty
					else if (envri == Envri.SITES) sites.map(Some(_))
					else None +: sites.map(Some(_))
				}
			}
			updateButton()
		}
	}

	private val onSiteSelected: () => Unit = () => {
		siteSelect.value.flatten.foreach { site =>
			whenDone(Backend.getSamplingPoints(site.uri)) { points =>
				samplingPointInput.setOptions {
					None +: points.map(Some(_)) :+ Some(customSamplingPoint)
				}
			}
			updateButton()
		}
	}

	private val onSamplingPointSelected: () => Unit = () => {
		samplingPointInput.value.flatten match {
			case Some(SamplingPoint(_, 0, 0, "Custom")) => positionElements.show()
			case _ => positionElements.hide()
		}
	}
	val stationSelect = new Select[Station]("stationselect", s => s"${s.id} (${s.name})", autoselect = true, cb = onStationSelected)
	val siteSelect = new Select[Option[Site]]("siteselect", _.map(_.name).getOrElse(""), cb = onSiteSelected)


	val samplingPointInput = new Select[Option[SamplingPoint]]("samplingpointselect", _.map(_.name).getOrElse(""), autoselect = false, onSamplingPointSelected)
	val latitudeInput = new DoubleOptInput("latitude", updateButton)
	val longitudeInput = new DoubleOptInput("longitude", updateButton)
	val samplingHeightInput = new FloatOptInput("sampleheight", updateButton)
	val instrUriInput = new UriOptionalOneOrSeqInput("instrumenturi", updateButton)

	val creatorInput = new UriInput("creatoruri", updateButton)
	val contributorsInput = new UriListInput("contributors", updateButton)
	val hostOrganizationInput = new UriOptInput("hostorganisation", updateButton)
	val commentInput = new TextOptInput("productioncomment", updateButton)
	val creationDateInput = new InstantInput("creationdate", updateButton)
	val sourcesInput = new HashOptListInput("sources", updateButton)

	val collectionTitle = new TextInput("collectiontitle", updateButton)
	val collectionDescription = new TextOptInput("collectiondescription", updateButton)
	val collectionMembers = new NonEmptyUriListInput("collectionmembers", updateButton)


	def dto: Try[UploadDto] = aboutPanel.itemType match {
		case Some(Data) => dataObjectDto
		case Some(Collection) => staticCollectionDto
		case Some(Document) => documentObjectDto
		case _ => fail("No file type selected")
	}

	def dataProductionDto: Try[Option[DataProductionDto]] = for(
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

	def dataObjectDto: Try[DataObjectDto] = for(
		submitter <- aboutPanel.submitter;
		file <- aboutPanel.itemName;
		hash <- aboutPanel.itemHash;
		previousVersion <- aboutPanel.previousVersion;
		doi <- aboutPanel.existingDoi;
		station <- stationSelect.value.withMissingError("Station not chosen");
		objSpec <- dataPanel.objSpec;
		acqInterval <- acqPanel.timeInterval;
		nRows <- dataPanel.nRows;
		latitude <- latitudeInput.value.withErrorContext("Latitude");
		longitude <- longitudeInput.value.withErrorContext("Longitude");
		samplingHeight <- samplingHeightInput.value.withErrorContext("Sampling height");
		instrumentUri <- instrUriInput.value.withErrorContext("Instrument URI");
		production <- if(productionElements.areEnabled) dataProductionDto else Success(None)
	) yield DataObjectDto(
		hashSum = hash,
		submitterId = submitter.id,
		objectSpecification = objSpec.uri,
		fileName = file,
		specificInfo = Right(
			StationDataMetadata(
				station = station.uri,
				site = siteSelect.value.flatten.map(_.uri),
				instrument = instrumentUri,
				samplingPoint = samplingPointInput.value.flatten match {
					case Some(SamplingPoint(_, 0, 0, "Custom")) => (latitude, longitude) match {
						case (Some(lat), Some(lon)) => Some(Position(lat.toDouble, lon.toDouble, None))
						case _ => None
					}
					case Some(position) => Some(Position(position.latitude, position.longitude, None))
					case _ => None
				},
				samplingHeight = samplingHeight,
				acquisitionInterval = acqInterval,
				nRows = nRows,
				production = production
			)
		),
		isNextVersionOf = previousVersion,
		preExistingDoi = doi,
		references = Some(
			References(
				citationString = None,
				keywords = dataPanel.keywords.toOption.map(_.split(",").toIndexedSeq.map(_.trim).filter(!_.isEmpty)).filter(!_.isEmpty)
			)
		)
	)
	def documentObjectDto: Try[DocObjectDto] = for(
		submitter <- aboutPanel.submitter;
		file <- aboutPanel.itemName;
		hash <- aboutPanel.itemHash;
		previousVersion <- aboutPanel.previousVersion;
		doi <- aboutPanel.existingDoi
	) yield DocObjectDto(
		hashSum = hash,
		submitterId = submitter.id,
		fileName = file,
		isNextVersionOf = previousVersion,
		preExistingDoi = doi
	)
	def staticCollectionDto: Try[StaticCollectionDto] = for(
		title <- collectionTitle.value.withErrorContext("Collection title");
		description <- collectionDescription.value;
		members <- collectionMembers.value.withErrorContext("Collection members (list of object urls)");
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

	private def handleDto(upDto: UploadDto): Unit = {
		hideAlert()
		resetForm()
		upDto match {
			case dto: DataObjectDto => {
				whenDone(Backend.getObjSpecs){ specs =>
					specs.find(_.uri == dto.objectSpecification).map{ spec =>
						levelControl.value = spec.dataLevel
						objSpecSelect.setOptions(specs.filter(_.dataLevel == spec.dataLevel))
						objSpecSelect.value = spec
						setupSpec(spec)
					}
				}
				dto.specificInfo match {
					case Left(_) =>
					case Right(acquisition) => {
						nRowsInput.value = acquisition.nRows
						aboutPanel.submitter.foreach{ submitter =>
							whenDone(Backend.stationInfo(submitter.producingOrganizationClass, submitter.producingOrganization)){ stations =>
								stations.find(_.uri == acquisition.station).map{ station =>
									stationSelect.value = station
									whenDone(Backend.getSites(station.uri)) { sites =>
										siteSelect.setOptions {
											if (sites.isEmpty) IndexedSeq.empty
											else if (envri == Envri.SITES) sites.map(Some(_))
											else None +: sites.map(Some(_))
										}
										acquisition.site.map(siteUri => sites.find(_.uri == siteUri).map { site =>
											siteSelect.value = Some(site)
											whenDone(Backend.getSamplingPoints(site.uri)) { points =>
												samplingPointInput.setOptions {
													None +: points.map(Some(_)) :+ Some(customSamplingPoint)
												}
												acquisition.samplingPoint.map{ point =>
													points.find(p => p.latitude == point.lat && p.longitude == point.lon) match {
														case Some(value) => samplingPointInput.value = Some(value)
														case None => {
															latitudeInput.value = Some(point.lat)
															longitudeInput.value = Some(point.lon)
															positionElements.show()
															samplingPointInput.value = Some(customSamplingPoint)
														}
													}
												}
											}
										})
										updateButton()
									}
									acquisition.acquisitionInterval.map{ time =>
										acqStartInput.value = time.start
										acqStopInput.value = time.stop
										timeIntevalInput.value = Some(time)
									}
									samplingHeightInput.value = acquisition.samplingHeight
									instrUriInput.value = acquisition.instrument
								}
							}
						}
						acquisition.production.map { production =>
							disableProductionButton()
							productionElements.show()
							creatorInput.value = production.creator
							contributorsInput.value = production.contributors
							hostOrganizationInput.value = production.hostOrganization
							commentInput.value = production.comment
							creationDateInput.value = production.creationDate
							sourcesInput.value = production.sources
						}
					}
				}
			}
			case dto: DocObjectDto =>
				updateButton()
			case dto: StaticCollectionDto =>
				collectionTitle.value = dto.title
				collectionMembers.value = dto.members
				collectionDescription.value = dto.description
				updateButton()
			case _ =>
		}
	}
}
