package se.lu.nateko.cp.meta.upload.subforms

import java.net.URI

import scala.concurrent.Future
import scala.util.{Try, Success}

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import se.lu.nateko.cp.meta.core.data.TimeInterval
import se.lu.nateko.cp.meta.upload._
import se.lu.nateko.cp.meta.{UploadDto, DataObjectDto}

import formcomponents._
import UploadApp.whenDone
import Utils._
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.StationDataMetadata


class AcquisitionPanel(implicit bus: PubSubBus, envri: Envri.Envri) extends PanelSubform(".acq-section"){
	def station: Try[Station] = stationSelect.value.withMissingError("Station not chosen")
	def site = siteSelect.value
	def timeInterval: Try[Option[TimeInterval]] = timeIntevalInput.value.withErrorContext("Acqusition time interval")
	def samplingHeight: Try[Option[Float]] = samplingHeightInput.value.withErrorContext("Sampling height")
	def instrUri: Try[OptionalOneOrSeq[URI]] = instrUriInput.value.withErrorContext("Instrument URI");

	def samplingPoint: Try[Option[Position]] = samplingPointSelect.value.flatten match {
		case Some(`customSamplingPoint`) =>
			for(
				latOpt <- latitudeInput.value.withErrorContext("Sampling point latitude");
				lonOpt <- longitudeInput.value.withErrorContext("Sampling point longitude")
			) yield
				for(lat <- latOpt; lon <- lonOpt) yield Position(lat, lon, None, None)

		case Some(point) => Success(Some(Position(point.latitude, point.longitude, None, None)))
		case _ => Success(None)
	}

	private val positionElements = new HtmlElements(".position-element")
	private val stationSelect = new Select[Station]("stationselect", s => s"${s.id} (${s.name})", autoselect = true, cb = onStationSelected)
	private val siteSelect = new Select[Option[NamedUri]]("siteselect", _.map(_.name).getOrElse(""), cb = onSiteSelected)
	private val acqStartInput = new InstantInput("acqstartinput", notifyUpdate)
	private val acqStopInput = new InstantInput("acqstopinput", notifyUpdate)
	private val timeIntevalInput = new TimeIntevalInput(acqStartInput, acqStopInput)
	private val samplingHeightInput = new FloatOptInput("sampleheight", notifyUpdate)
	private val instrUriInput = new UriOptionalOneOrSeqInput("instrumenturi", notifyUpdate)
	private val samplingPointSelect = new Select[Option[SamplingPoint]]("samplingpointselect", _.map(_.name).getOrElse(""), autoselect = false, onSamplingPointSelected)
	private val latitudeInput = new DoubleOptInput("latitude", notifyUpdate)
	private val longitudeInput = new DoubleOptInput("longitude", notifyUpdate)

	private val customSamplingPoint = SamplingPoint(new URI(""), 0, 0, "Custom")

	def resetForm(): Unit = {
		resetPlaceInfo()
		acqStartInput.reset()
		acqStopInput.reset()
		samplingHeightInput.value = None
		instrUriInput.value = None
	}

	bus.subscribe{
		case LevelSelected(level) => onLevelSelected(level)
		case ObjSpecSelected(objSpec) =>
			onLevelSelected(objSpec.dataLevel)
			if (objSpec.hasDataset) {
				acqStartInput.disable()
				acqStopInput.disable()
			} else {
				acqStartInput.enable()
				acqStopInput.enable()
			}

		case GotStationsList(stations) =>
			stationSelect.setOptions(stations)

		case GotUploadDto(dto) => handleDto(dto)

	}

	private def onLevelSelected(level: Int): Unit = if(level <= 2) show() else hide()

	private def handleDto(upDto: UploadDto): Unit = upDto match {
		case dto: DataObjectDto =>
			dto.specificInfo match {
				case Right(l2) =>
					l2.acquisitionInterval.fold(acqStartInput.reset())(i => acqStartInput.value = i.start)
					l2.acquisitionInterval.fold(acqStopInput.reset())(i => acqStopInput.value = i.stop)
					timeIntevalInput.value = l2.acquisitionInterval
					samplingHeightInput.value = l2.samplingHeight
					instrUriInput.value = l2.instrument

					whenDone(AcquisitionPanel.getStationInfo(l2, stationSelect.getOptions)){
						case None =>
							resetPlaceInfo()
						case Some((station, sitesInfo)) =>
							stationSelect.value = station
							initSitesOptions(sitesInfo.all)
							siteSelect.value = sitesInfo.selected
							initSamplingPointOptions(sitesInfo.points.all)
							val customPosOpt: Option[Position] = sitesInfo.points.selected.flatMap(_.left.toOption)
							latitudeInput.value = customPosOpt.map(_.lat)
							longitudeInput.value = customPosOpt.map(_.lon)
							samplingPointSelect.value = sitesInfo.points.selected.map(_.fold(_ => customSamplingPoint, identity))
					}.andThen{_ =>
						onSamplingPointSelected()
						show()
						notifyUpdate()
					}
				case _ =>
					hide()
			}
		case _ =>
			hide()
	}

	private def onStationSelected(): Unit = stationSelect.value.foreach { station =>
		whenDone(Backend.getSites(station.uri))(initSitesOptions)
		notifyUpdate()
	}

	private def initSitesOptions(sites: IndexedSeq[NamedUri]): Unit = siteSelect.setOptions {
		if (sites.isEmpty) IndexedSeq.empty
		else if (envri == Envri.SITES) sites.map(Some(_))
		else None +: sites.map(Some(_))
	}

	private def onSiteSelected(): Unit = siteSelect.value.flatten.foreach { site =>
		whenDone(Backend.getSamplingPoints(site.uri))(initSamplingPointOptions)
		notifyUpdate()
	}

	private def initSamplingPointOptions(points: IndexedSeq[SamplingPoint]): Unit = samplingPointSelect.setOptions {
		None +: points.map(Some(_)) :+ Some(customSamplingPoint)
	}

	private def onSamplingPointSelected(): Unit = {
		samplingPointSelect.value.flatten match {
			case Some(`customSamplingPoint`) => positionElements.show()
			case _ => positionElements.hide()
		}
	}

	private def resetPlaceInfo(): Unit = {
		stationSelect.reset()
		siteSelect.setOptions(IndexedSeq.empty)
		samplingPointSelect.setOptions(IndexedSeq.empty)
		latitudeInput.reset()
		longitudeInput.reset()
	}

}

object AcquisitionPanel{
	class SamplingPoints(
		val all: IndexedSeq[SamplingPoint],
		val selected: Option[Either[Position, SamplingPoint]]
	)
	class Sites(val all: IndexedSeq[NamedUri], val selected: Option[NamedUri], val points: SamplingPoints)

	def getStationInfo(l2: StationDataMetadata, stations: IndexedSeq[Station]): Future[Option[(Station, Sites)]] =
		stations.find(_.uri == l2.station).fold(
			Future.successful[Option[(Station, Sites)]](None)
		){station =>
			Backend.getSites(station.uri).flatMap{allSites =>
				val selected = l2.site.flatMap{uri => allSites.find(_.uri == uri)}
				val pointsFut: Future[SamplingPoints] = selected.fold(
					Future.successful(new SamplingPoints(IndexedSeq.empty, None))
				){site =>
					Backend.getSamplingPoints(site.uri).map{allPoints =>
						val selectedPoint = l2.samplingPoint.map{pos =>
							allPoints
								.find{sp => sp.latitude == pos.lat && sp.longitude == pos.lon}
								.fold[Either[Position, SamplingPoint]](Left(pos))(Right(_))
						}
						new SamplingPoints(allPoints, selectedPoint)
					}
				}
				pointsFut.map{points =>
					Some(station -> new Sites(allSites, selected, points))
				}
			}
		}
}