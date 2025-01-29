package se.lu.nateko.cp.meta.upload.subforms

import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.LatLonBox
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.upload.PubSubBus
import se.lu.nateko.cp.meta.upload.SpatialCoverage
import se.lu.nateko.cp.meta.upload.UploadApp
import se.lu.nateko.cp.meta.upload.Utils.*
import se.lu.nateko.cp.meta.upload.formcomponents.DoubleInput
import se.lu.nateko.cp.meta.upload.formcomponents.HtmlElements
import se.lu.nateko.cp.meta.upload.formcomponents.Select
import se.lu.nateko.cp.meta.upload.formcomponents.JsonInput
import se.lu.nateko.cp.meta.upload.formcomponents.TextOptInput

import java.net.URI
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import se.lu.nateko.cp.meta.GeoCoverage
import se.lu.nateko.cp.meta.GeoJsonString
import se.lu.nateko.cp.meta.core.data.FeatureWithGeoJson


class GeoCoverageSelector(covs: IndexedSeq[SpatialCoverage], lbl: String)(using PubSubBus) extends PanelSubform(s".geocov-element"):
	private val spatialCovSelect = new Select[SpatialCoverage](s"${lbl}geoselect", _.label, _.uri.map(_.toString).getOrElse(""), autoselect = false, onSpatCoverSelected)
	private val geoCovElements = new HtmlElements(s".geocov-element")
	private val latLonBoxElements = new HtmlElements(s".latlonbox-element")
	private val geoJsonElements = new HtmlElements(s".customgeo-element")

	private val spatCovLabel = new TextOptInput(s"${lbl}geolbl", () => ())
	private val geoJsonInput = new JsonInput(s"${lbl}geojson", notifyUpdate, "geocoverage")
	private val minLatInput = new DoubleInput(s"${lbl}geominlat", notifyUpdate)
	private val minLonInput = new DoubleInput(s"${lbl}geominlon", notifyUpdate)
	private val maxLatInput = new DoubleInput(s"${lbl}geomaxlat", notifyUpdate)
	private val maxLonInput = new DoubleInput(s"${lbl}geomaxlon", notifyUpdate)

	private val customLatLonBox = new SpatialCoverage(None, "Custom spatial coverage (Lat/lon box)")
	private val customSpatCov = new SpatialCoverage(None, "Custom spatial coverage from GeoJson")
	spatialCovSelect.setOptions(customLatLonBox +: customSpatCov +: covs)

	def spatialCoverage: Try[Option[GeoCoverage]] = spatialCovSelect.value match
		case None  => Success(None)
		case Some(`customLatLonBox`) =>
			for
				minLat <- minLatInput.value
				minLon <- minLonInput.value
				maxLat <- maxLatInput.value
				maxLon <- maxLonInput.value
				label <- spatCovLabel.value
			yield Some(LatLonBox(Position.ofLatLon(minLat, minLon), Position.ofLatLon(maxLat, maxLon), label, None))

		case Some(`customSpatCov`) =>
			geoJsonInput.value.map(GeoJsonString.unsafe).map(Some.apply).withErrorContext("custom spatial coverage")
		case Some(spCov) => Success(spCov.uri)


	private def onSpatCoverSelected(): Unit =
		if(spatialCovSelect.value == Some(customLatLonBox))
			geoJsonElements.hide()
			latLonBoxElements.show()
		else if (spatialCovSelect.value == Some(customSpatCov))
			latLonBoxElements.hide()
			geoJsonElements.show()
		else
			geoCovElements.hide()
		notifyUpdate()

	def handleReceivedSpatialCoverage(spatCov: Option[GeoCoverage]): Unit = spatCov match
		case None => resetForm()

		case Some(FeatureWithGeoJson(cov, geoJson)) =>

			resetCustomCovElems()
			geoCovElements.hide()

			covs.find(_.uri == cov.uri) match

				case Some(stockCov) =>
					spatialCovSelect.value = stockCov

				case None => cov match
					case b: LatLonBox =>
						minLatInput.value = b.min.lat
						minLonInput.value = b.min.lon
						maxLatInput.value = b.max.lat
						maxLonInput.value = b.max.lon
						spatialCovSelect.value = customLatLonBox
						geoJsonElements.hide()
						latLonBoxElements.show()
					case _ =>
						spatialCovSelect.value = customSpatCov
						geoJsonInput.value = geoJson
						latLonBoxElements.hide()
						geoJsonElements.show()
						spatCovLabel.value = cov.label
		case _ =>
			UploadApp.showAlert("Fetched metadata had an unexpected format", "alert alert-warning")

	private def resetCustomCovElems(): Unit =
		spatCovLabel.reset()
		Seq(minLatInput, minLonInput, maxLatInput, maxLonInput, geoJsonInput).foreach(_.reset())

	def resetForm(): Unit =
		spatialCovSelect.reset()
		resetCustomCovElems()
		geoCovElements.hide()

	def unselect(): Unit =
		spatialCovSelect.reset()
		geoCovElements.hide()
end GeoCoverageSelector
