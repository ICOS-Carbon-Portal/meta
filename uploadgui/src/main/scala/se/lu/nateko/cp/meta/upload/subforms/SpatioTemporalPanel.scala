package se.lu.nateko.cp.meta.upload.subforms


import scala.util.{Try, Success}

import se.lu.nateko.cp.meta.upload.*
import se.lu.nateko.cp.meta.{UploadDto, DataObjectDto, DataProductionDto, SpatioTemporalDto}

import formcomponents.*
import Utils.*
import se.lu.nateko.cp.meta.core.data.TemporalCoverage
import se.lu.nateko.cp.meta.core.data.LatLonBox
import java.net.URI
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.upload.UploadApp

class SpatioTemporalPanel(covs: IndexedSeq[SpatialCoverage])(implicit bus: PubSubBus) extends PanelSubform(".l3-section"){

	def meta(productionDto: => Try[DataProductionDto]): Try[SpatioTemporalDto] = for(
		title <- titleInput.value;
		descr <- descriptionInput.value;
		spatCov <- spatialCoverage;
		tempCovOpt <- timeIntevalInput.value;
		tempCov <- tempCovOpt.withMissingError("time interval");
		tempRes <- temporalResInput.value;
		prod <- productionDto;
		customLanding <- externalPageInput.value;
		height <- samplingHeightInput.value;
		varInfo <- varInfoForm.varInfos
	) yield SpatioTemporalDto(
		title = title,
		description = descr,
		spatial = spatCov,
		temporal = TemporalCoverage(tempCov, tempRes),
		forStation = stationSelect.value.map(_.namedUri.uri),
		samplingHeight = height,
		production = prod,
		customLandingPage = customLanding,
		variables = varInfo
	)

	def spatialCoverage: Try[Either[GeoFeature, URI]] = spatialCovSelect
		.value.withMissingError("spatial coverage").flatMap{spCov =>
			if(spCov eq customLatLonBox)
				for(
					minLat <- minLatInput.value;
					minLon <- minLonInput.value;
					maxLat <- maxLatInput.value;
					maxLon <- maxLonInput.value;
					label <- spatCovLabel.value
				) yield Left(LatLonBox(Position.ofLatLon(minLat, minLon), Position.ofLatLon(maxLat, maxLon), label, None))
			else if (spCov eq customSpatCov) Success(Left(gf))
			else Success(Right(spCov.uri))
		}

	def varnames: Try[Option[Seq[String]]] = varInfoForm.varInfos

	private val spatCoverElements = new HtmlElements(".l3spatcover-element")
	private val titleInput = new TextInput("l3title", notifyUpdate, "elaborated product title")
	private val descriptionInput = new DescriptionInput("l3descr", notifyUpdate)
	private val timeStartInput = new InstantInput("l3startinput", notifyUpdate)
	private val timeStopInput = new InstantInput("l3stopinput", notifyUpdate)
	private val timeIntevalInput = new TimeIntevalInput(timeStartInput, timeStopInput)
	private val temporalResInput = new TextOptInput("l3tempres", notifyUpdate)
	private val stationSelect = new Select[Station]("elabstationselect", s => s"${s.id} (${s.namedUri.name})")
	private val samplingHeightInput = new FloatOptInput("elabsampleheight", notifyUpdate)
	private val spatialCovSelect = new Select[SpatialCoverage]("l3spatcoverselect", _.label, autoselect = false, onSpatCoverSelected)
	private val varInfoForm = new L3VarInfoForm("l3varinfo-form", notifyUpdate)
	private val externalPageInput = new UriOptInput("l3landingpage", notifyUpdate)

	private val spatCovLabel = new TextOptInput("l3spatcoverlabel", () => ())
	private val minLatInput = new DoubleInput("l3minlat", notifyUpdate)
	private val minLonInput = new DoubleInput("l3minlon", notifyUpdate)
	private val maxLatInput = new DoubleInput("l3maxlat", notifyUpdate)
	private val maxLonInput = new DoubleInput("l3maxlon", notifyUpdate)

	private var gf: GeoFeature = null

	private val customLatLonBox = new SpatialCoverage(null, "Custom spatial coverage (Lat/lon box)")
	private val customSpatCov = new SpatialCoverage(null, "Custom spatial coverage")
	private val customSpatCovWarningMsg = "The data object has custom spatial coverage which cannot be updated in UploadGUI. All other metadata can be updated."

	def resetSpatialCovOptions = spatialCovSelect.setOptions(customLatLonBox +: covs)

	def resetForm(): Unit = {
		Iterable(
			titleInput, descriptionInput, timeStartInput, timeStopInput,
			temporalResInput, externalPageInput
		).foreach(_.reset())
		spatialCovSelect.reset()
		varInfoForm.setValues(None)
		resetLatLonBox()
		resetSpatialCovOptions
		spatialCovSelect.enable()
	}

	bus.subscribe{
		case GotUploadDto(upDto) => handleDto(upDto)
		case LevelSelected(_) => hide()
		case ObjSpecSelected(spec) =>
			if(spec.isSpatiotemporal) show() else hide()
		case GotStationsList(stations) => stationSelect.setOptions(stations)
	}

	private def onSpatCoverSelected(): Unit =
		if(spatialCovSelect.value == Some(customLatLonBox))
			spatCoverElements.show()
			UploadApp.hideAlert()
		else if (spatialCovSelect.value == Some(customSpatCov))
			UploadApp.showAlert(customSpatCovWarningMsg, "alert alert-warning")
			spatCoverElements.hide()
		else
			spatCoverElements.hide()
			UploadApp.hideAlert()
		notifyUpdate()

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
				varInfoForm.setValues(spatTemp.variables)
				spatTemp.spatial match{
					case Left(cov) =>
						cov match
							case b: LatLonBox =>
								resetSpatialCovOptions
								minLatInput.value = b.min.lat
								minLonInput.value = b.min.lon
								maxLatInput.value = b.max.lat
								maxLonInput.value = b.max.lon
								spatialCovSelect.value = customLatLonBox
								spatCoverElements.show()
								spatialCovSelect.enable()
							case cov: GeoFeature =>
								spatialCovSelect.setOptions(IndexedSeq(customSpatCov))
								gf = cov
								spatCoverElements.hide()
								spatialCovSelect.value = customSpatCov
								spatialCovSelect.disable()
								UploadApp.showAlert(customSpatCovWarningMsg, "alert alert-warning")
						spatCovLabel.value = cov.label
					case Right(covUri) =>
						resetLatLonBox()
						spatCoverElements.hide()
						resetSpatialCovOptions
						covs.find(_.uri == covUri).fold(spatialCovSelect.reset()){
							cov => spatialCovSelect.value = cov
						}
				}
				show()
			case _ =>
				hide()
		}
		case _ =>
			hide()
	}

	private def resetLatLonBox(): Unit = {
		spatCovLabel.reset()
		Seq(minLatInput, minLonInput, maxLatInput, maxLonInput).foreach(_.reset())
	}
}
