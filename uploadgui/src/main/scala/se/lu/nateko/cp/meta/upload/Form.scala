package se.lu.nateko.cp.meta.upload

import org.scalajs.dom
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.{StationDataMetadata, SubmitterProfile, DataObjectDto, DocObjectDto, UploadDto, StaticCollectionDto, DataProductionDto}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Failure, Success, Try}
import scala.concurrent.Future
import Utils._
import FormTypeRadio._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

class Form(
	onUpload: (UploadDto, Option[dom.File]) => Unit,
	onSubmitterSelect: SubmitterProfile => Future[IndexedSeq[Station]]
)(implicit envri: Envri.Envri, envriConf: EnvriConfig) {

	val formElement = new FormElement("form-block")
	val fileElement = new HtmlElements("#file-element")
	val fileInputElement = new HtmlElements("#fileinput")
	val filenameElement = new HtmlElements("#filename")
	val metadataUrlElement = new HtmlElements("#metadata-url")

	def resetForm() = {
		val subm = submitterIdSelect.value
		val mode = newUpdateControl.value
		formElement.reset()
		subm.map(s => submitterIdSelect.value = s)
		onSubmitterSelected()
		mode.map(s => newUpdateControl.value = s)
		updateButton()
	}

	val onNewUpdateSelected: String => Unit = _ match {
		case "new" =>
			resetForm()
			fileInputElement.show()
			filenameElement.hide()
			metadataUrlElement.hide()
			typeControl.enable()
		case "update" =>
			resetForm()
			fileInputElement.hide()
			filenameElement.show()
			metadataUrlElement.show()
			typeControl.disable()
	}

	val dataElements = new HtmlElements(".data-section")
	val collectionElements = new HtmlElements(".collection-section")
	val productionElements = new HtmlElements(".production-section")

	val onFormTypeSelected: FormType => Unit = formType => {
		dataElements.hide()
		collectionElements.hide()
		fileElement.show()
		productionElements.hide()
		disableProductionButton()

		formType match {
			case Data => {
				dataElements.show()
				enableProductionButton()
			}
			case Collection => {
				collectionElements.show()
				fileElement.hide()
			}
			case Document =>
		}
		updateButton()
	}

	def submitAction(): Unit = {
		dom.window.scrollTo(0, 0)
		submitButton.disable("")
		hideAlert()
		showProgressBar()
		typeControl.formType match {
			case Data =>
				newUpdateControl.value match {
					case Some("new") =>
						whenDone {
								if (fileInput.hasBeenModified)
									fileInput.rehash
								else
									Future.successful(())
						}{ _ =>
							for(dto <- dataObjectDto; file <- fileInput.file; nRows <- nRowsInput.value; spec <- objSpecSelect.value) {
								whenDone(Backend.tryIngestion(file, spec, nRows)){ _ =>
									onUpload(dto, Some(file))
								}.failed.foreach {
									case _ => hideProgressBar()
								}
							}
						}
					case _ =>
						for(dto <- dataObjectDto) {
							onUpload(dto, None)
						}
				}
			case Collection =>
				for(dto <- staticCollectionDto) {
					onUpload(dto, None)
				}
			case Document =>
				newUpdateControl.value match {
					case Some("new") =>
						for(dto <- documentObjectDto; file <- fileInput.file) {
							onUpload(dto, Some(file))
						}
					case _ =>
						for(dto <- documentObjectDto) {
							onUpload(dto, None)
						}
				}
		}
	}
	val submitButton = new Button("submitbutton", () => submitAction())
	val updateButton: () => Unit = () => dto match {
		case Success(_) => submitButton.enable()
		case Failure(err) => submitButton.disable(err.getMessage)
	}

	val getMetadataButton = new Button("get-metadata", getMetadata)
	val updateGetMetadataButton: () => Unit = () => {
		(submitterIdSelect.value.withMissingError("Submitter Id not set"), metadataUriInput.value) match {
			case (Success(_), Success(_)) => getMetadataButton.enable()
			case (Failure(err), _) => getMetadataButton.disable(err.getMessage)
			case (_, Failure(err)) => getMetadataButton.disable(err.getMessage)
		}
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

	val onLevelSelected: String => Unit = (level: String) =>
		whenDone{
			Backend.getObjSpecs.map(_.filter(_.dataLevel == level.toInt))
		}(objSpecSelect.setOptions)

	val onSubmitterSelected: () => Unit = () =>
		submitterIdSelect.value.foreach{submitter =>
			whenDone(onSubmitterSelect(submitter)){stations =>
				stationSelect.setOptions(stations)
				updateButton()
				updateGetMetadataButton()
			}
		}

	private val setupSpec: ObjSpec => Unit = objSpec => {
		if (objSpec.hasDataset) {
			nRowsInput.enable()
			acqStartInput.disable()
			acqStopInput.disable()
		} else {
			nRowsInput.disable()
			acqStartInput.enable()
			acqStopInput.enable()
		}
	}

	private val onSpecSelected: () => Unit = () => {
		objSpecSelect.value.foreach{ objSpec =>
			setupSpec(objSpec)
		}
		updateButton()
	}

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

	val fileInput = new FileInput("fileinput", updateButton)
	val fileNameText = new TextInput("filename", updateButton)
	var fileHash: Option[Sha256Sum] = None
	val newUpdateControl = new Radio[String]("new-update-radio", onNewUpdateSelected, s => s)
	val typeControl = new FormTypeRadio("file-type-radio", onFormTypeSelected)

	val previousVersionInput = new HashOptInput("previoushash", updateButton)
	val existingDoiInput = new DoiOptInput("existingdoi", updateButton)
	val levelControl = new Radio[Int]("level-radio", onLevelSelected, i => i.toString())
	val stationSelect = new Select[Station]("stationselect", s => s"${s.id} (${s.name})", autoselect = true, cb = onStationSelected)
	val siteSelect = new Select[Option[Site]]("siteselect", _.map(_.name).getOrElse(""), cb = updateButton)
	val objSpecSelect = new Select[ObjSpec]("objspecselect", _.name, cb = onSpecSelected)
	val nRowsInput = new IntOptInput("nrows", updateButton)
	val keywordsInput = new TextInput("keywords", () => ())

	val submitterIdSelect = new Select[SubmitterProfile]("submitteridselect", _.id, autoselect = true, onSubmitterSelected)

	val acqStartInput = new InstantInput("acqstartinput", updateButton)
	val acqStopInput = new InstantInput("acqstopinput", updateButton)
	val samplingHeightInput = new FloatOptInput("sampleheight", updateButton)
	val instrUriInput = new UriOptionalOneOrSeqInput("instrumenturi", updateButton)
	val timeIntevalInput = new TimeIntevalInput(acqStartInput, acqStopInput)

	val creatorInput = new UriInput("creatoruri", updateButton)
	val contributorsInput = new UriListInput("contributors", updateButton)
	val hostOrganizationInput = new UriOptInput("hostorganisation", updateButton)
	val commentInput = new TextOptInput("productioncomment", updateButton)
	val creationDateInput = new InstantInput("creationdate", updateButton)
	val sourcesInput = new HashOptListInput("sources", updateButton)

	val collectionTitle = new TextInput("collectiontitle", updateButton)
	val collectionDescription = new TextOptInput("collectiondescription", updateButton)
	val collectionMembers = new NonEmptyUriListInput("collectionmembers", updateButton)

	val metadataUriInput = new UriInput("metadata-update", updateGetMetadataButton)

	def dto: Try[UploadDto] = typeControl.formType match {
		case Data => dataObjectDto
		case Collection => staticCollectionDto
		case Document => documentObjectDto
	}
	private def isTypeSelected = if (typeControl.value.isEmpty) fail("No file type selected") else Success(())

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
		submitter <- submitterIdSelect.value.withMissingError("Submitter Id not set");
		file <- if(newUpdateControl.value == Some("new")) fileInput.file.map(_.name) else fileNameText.value;
		hash <- if(newUpdateControl.value == Some("new")) fileInput.hash else Success(fileHash.get);
		_ <- isTypeSelected;
		previousVersion <- previousVersionInput.value.withErrorContext("Previous version");
		doi <- existingDoiInput.value.withErrorContext("Pre-existing DOI");
		station <- stationSelect.value.withMissingError("Station not chosen");
		objSpec <- objSpecSelect.value.withMissingError("Data type not set");
		acqInterval <- timeIntevalInput.value.withErrorContext("Acqusition time interval");
		nRows <- nRowsInput.value.withErrorContext("Number of rows");
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
				keywords = keywordsInput.value.toOption.map(_.split(",").toIndexedSeq.map(_.trim).filter(!_.isEmpty)).filter(!_.isEmpty)
			)
		)
	)
	def documentObjectDto: Try[DocObjectDto] = for(
		file <- if(newUpdateControl.value == Some("new")) fileInput.file.map(_.name) else fileNameText.value;
		hash <- if(newUpdateControl.value == Some("new")) fileInput.hash else Success(fileHash.get);
		_ <- isTypeSelected;
		previousVersion <- previousVersionInput.value.withErrorContext("Previous version");
		doi <- existingDoiInput.value.withErrorContext("Pre-existing DOI");
		submitter <- submitterIdSelect.value.withMissingError("Submitter Id not set")
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
		_ <- isTypeSelected;
		previousVersion <- previousVersionInput.value.withErrorContext("Previous version");
		doi <- existingDoiInput.value.withErrorContext("Pre-existing DOI");
		submitter <- submitterIdSelect.value.withMissingError("Submitter Id not set")
	) yield StaticCollectionDto(
		submitterId = submitter.id,
		members = members,
		title = title,
		description = description,
		isNextVersionOf = previousVersion,
		preExistingDoi = doi
	)

	def getMetadata(): Unit = {
		hideAlert()
		metadataUriInput.value.map { metadataUri =>
			resetForm()
			whenDone(Backend.getMetadata(metadataUri)) {
				case dto: DataObjectDto => {
					typeControl.value = Data
					onFormTypeSelected(Data)
					fileNameText.value = dto.fileName
					fileHash = Some(dto.hashSum)
					previousVersionInput.value = dto.isNextVersionOf
					existingDoiInput.value = dto.preExistingDoi
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
							submitterIdSelect.value.map{ submitter =>
								whenDone(Backend.stationInfo(submitter.producingOrganizationClass, submitter.producingOrganization)){ stations =>
									stations.find(_.uri == acquisition.station).map{ station =>
										stationSelect.value = station
										whenDone(Backend.getSites(station.uri)) { sites =>
											siteSelect.setOptions {
												if (sites.isEmpty) IndexedSeq.empty
												else if (envri == Envri.SITES) sites.map(Some(_))
												else None +: sites.map(Some(_))
											}
											acquisition.site.map(siteUri => sites.find(_.uri == siteUri).map(site => siteSelect.value = Some(site)))
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
					typeControl.value = Document
					onFormTypeSelected(Document)
					fileNameText.value = dto.fileName
					fileHash = Some(dto.hashSum)
					previousVersionInput.value = dto.isNextVersionOf
					existingDoiInput.value = dto.preExistingDoi
					updateButton()
				case dto: StaticCollectionDto =>
					typeControl.value = Collection
					onFormTypeSelected(Collection)
					collectionTitle.value = dto.title
					collectionMembers.value = dto.members
					collectionDescription.value = dto.description
					previousVersionInput.value = dto.isNextVersionOf
					existingDoiInput.value = dto.preExistingDoi
					updateButton()
				case _ =>
					showAlert(s"$metadataUri cannot be found", "alert alert-danger")
			}
		}
	}
}
