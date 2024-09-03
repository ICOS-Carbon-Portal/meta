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
import se.lu.nateko.cp.meta.upload.formcomponents.TextOptInput

import java.net.URI
import scala.util.Failure
import scala.util.Success
import scala.util.Try


class GeoCoverageSelector(covs: IndexedSeq[SpatialCoverage], lbl: String)(using PubSubBus) extends PanelSubform(s".geocov-element"):
	private val spatialCovSelect = new Select[SpatialCoverage](s"${lbl}geoselect", _.label, autoselect = false, onSpatCoverSelected)
	private val spatCoverElements = new HtmlElements(s".geocov-element")

	private var customSpatCovMeta: Option[GeoFeature] = None

	private val spatCovLabel = new TextOptInput(s"${lbl}geolbl", () => ())
	private val minLatInput = new DoubleInput(s"${lbl}geominlat", notifyUpdate)
	private val minLonInput = new DoubleInput(s"${lbl}geominlon", notifyUpdate)
	private val maxLatInput = new DoubleInput(s"${lbl}geomaxlat", notifyUpdate)
	private val maxLonInput = new DoubleInput(s"${lbl}geomaxlon", notifyUpdate)

	private val customLatLonBox = new SpatialCoverage(null, "Custom spatial coverage (Lat/lon box)")
	private val customSpatCov = new SpatialCoverage(null, "Custom spatial coverage")
	private val customSpatCovWarningMsg = "This item has a custom spatial coverage, which cannot be updated in UploadGUI"

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
			else if (spCov eq customSpatCov) Try(customSpatCovMeta.get).map: gf =>
				gf.uri.fold(Left(gf))(Right(_))
			else Success(Right(spCov.uri))
		}

	def resetSpatialCovOptions(): Unit = spatialCovSelect.setOptions(customLatLonBox +: covs)

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

	def handleReceivedSpatialCoverage(spatCov: Either[GeoFeature, URI]): Unit = spatCov match
		case Left(cov) =>
			cov match
				case b: LatLonBox =>
					resetSpatialCovOptions()
					minLatInput.value = b.min.lat
					minLonInput.value = b.min.lon
					maxLatInput.value = b.max.lat
					maxLonInput.value = b.max.lon
					spatialCovSelect.value = customLatLonBox
					spatCoverElements.show()
					spatialCovSelect.enable()
				case cov: GeoFeature =>
					spatialCovSelect.setOptions(IndexedSeq(customSpatCov))
					customSpatCovMeta = Some(cov)
					spatCoverElements.hide()
					spatialCovSelect.value = customSpatCov
					spatialCovSelect.disable()
					UploadApp.showAlert(customSpatCovWarningMsg, "alert alert-warning")
			spatCovLabel.value = cov.label
		case Right(covUri) =>
			resetLatLonBox()
			spatCoverElements.hide()
			resetSpatialCovOptions()
			covs.find(_.uri == covUri).fold(spatialCovSelect.reset()){
				cov => spatialCovSelect.value = cov
			}

	private def resetLatLonBox(): Unit =
		spatCovLabel.reset()
		Seq(minLatInput, minLonInput, maxLatInput, maxLonInput).foreach(_.reset())

	def resetForm(): Unit =
		spatialCovSelect.reset()
		resetLatLonBox()
		resetSpatialCovOptions()
		spatialCovSelect.enable()

end GeoCoverageSelector
