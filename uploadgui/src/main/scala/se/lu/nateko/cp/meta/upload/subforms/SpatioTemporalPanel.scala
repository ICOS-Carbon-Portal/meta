package se.lu.nateko.cp.meta.upload.subforms


import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.DataProductionDto
import se.lu.nateko.cp.meta.SpatioTemporalDto
import se.lu.nateko.cp.meta.UploadDto
import se.lu.nateko.cp.meta.core.data.TemporalCoverage
import se.lu.nateko.cp.meta.upload.*
import se.lu.nateko.cp.meta.upload.UploadApp.whenDone

import scala.util.Try

import formcomponents.*
import Utils.*
import java.net.URI

class SpatioTemporalPanel(covs: IndexedSeq[SpatialCoverage])(implicit bus: PubSubBus) extends PanelSubform(".l3-section"){

	def meta(productionDto: => Try[DataProductionDto]): Try[SpatioTemporalDto] = for
		title <- titleInput.value;
		descr <- descriptionInput.value;
		spatCovOpt <- spatialCovSelect.spatialCoverage;
		spatCov <- spatCovOpt.withMissingError("spatial coverage");
		tempCovOpt <- timeIntevalInput.value;
		tempCov <- tempCovOpt.withMissingError("time interval");
		tempRes <- temporalResInput.value;
		prod <- productionDto;
		customLanding <- externalPageInput.value;
		height <- samplingHeightInput.value;
		varInfo <- varInfoForm.values
	yield SpatioTemporalDto(
		title = title,
		description = descr,
		spatial = spatCov,
		temporal = TemporalCoverage(tempCov, tempRes),
		forStation = stationSelect.value.map(_.namedUri.uri),
		samplingHeight = height,
		production = prod,
		customLandingPage = customLanding,
		variables = varInfo.map(_.map(_.uri.toString.split('/').last))
	)

	def varnames: Try[Option[Seq[String]]] = varInfoForm.values.map(_.map(_.map(_.title)))

	private val titleInput = new TextInput("l3title", notifyUpdate, "elaborated product title")
	private val descriptionInput = new DescriptionInput("l3descr", notifyUpdate)
	private val timeStartInput = new InstantInput("l3startinput", notifyUpdate)
	private val timeStopInput = new InstantInput("l3stopinput", notifyUpdate)
	private val timeIntevalInput = new TimeIntevalInput(timeStartInput, timeStopInput)
	private val temporalResInput = new TextOptInput("l3tempres", notifyUpdate)
	private val stationSelect = new Select[Station]("elabstationselect", s => s"${s.id} (${s.namedUri.name})", _.namedUri.uri.toString)
	private val samplingHeightInput = new FloatOptInput("elabsampleheight", notifyUpdate)
	private val spatialCovSelect = new GeoCoverageSelector(covs, "spattemp")
	private val varInfoForm = new L3VarInfoForm("l3varinfo-form", notifyUpdate)
	private val externalPageInput = new UriOptInput("l3landingpage", notifyUpdate)
	private var datasetSpec: Option[URI] = None
	private var selsectedVars: Option[Seq[String]] = None

	def resetForm(): Unit = {
		Iterable(
			titleInput, descriptionInput, timeStartInput, timeStopInput,
			temporalResInput, externalPageInput
		).foreach(_.reset())
		varInfoForm.setValues(None)
		spatialCovSelect.resetForm()
	}

	bus.subscribe{
		case GotUploadDto(upDto) => handleDto(upDto)
		case LevelSelected(_) => hide()
		case ObjSpecSelected(spec) =>
			if(spec.isSpatiotemporal) show() else hide()
			if(spec.isNetCDF) varInfoForm.show() else varInfoForm.hide()
			datasetSpec = spec.dataset
		case GotStationsList(stations) => stationSelect.setOptions(stations)
		case GotVariableList(variables) =>
			varInfoForm.list = variables
			selsectedVars.map { variables =>
				varInfoForm.setValues(Some(variables.flatMap(uri => varInfoForm.list.find(_.uri.toString.split('/').last == uri))))
			}

	}

	private def handleDto(upDto: UploadDto): Unit = upDto match {
		case dto: DataObjectDto => dto.specificInfo match{
			case Left(spatTemp) =>
				titleInput.value = spatTemp.title
				descriptionInput.value = spatTemp.description
				timeStartInput.value = spatTemp.temporal.interval.start
				timeStopInput.value = spatTemp.temporal.interval.stop
				temporalResInput.value = spatTemp.temporal.resolution
				stationSelect.reset()
				for(
					statUri <- spatTemp.forStation;
					stat <- stationSelect.getOptions.find(_.namedUri.uri == statUri)
				) stationSelect.value = stat
				samplingHeightInput.value = spatTemp.samplingHeight
				externalPageInput.value = spatTemp.customLandingPage
				selsectedVars = spatTemp.variables
				spatTemp.variables.map { varUris =>
					datasetSpec.map { dataset => 
						whenDone(getVariables(dataset)) { variables =>
							varInfoForm.list = variables
							varInfoForm.setValues(Some(varUris.flatMap(uri => varInfoForm.list.find(_.uri.toString.split('/').last == uri))))
						}
					}
				}
				spatialCovSelect.handleReceivedSpatialCoverage(Some(spatTemp.spatial))
				show()
			case _ =>
				hide()
		}
		case _ =>
			hide()
	}
}
