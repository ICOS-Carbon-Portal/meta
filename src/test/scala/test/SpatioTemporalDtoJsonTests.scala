package se.lu.nateko.cp.meta.test

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.core.data.{TemporalCoverage, TimeInterval}
import se.lu.nateko.cp.meta.{CpmetaJsonProtocol, DataProductionDto, SpatioTemporalDto}
import spray.json.*

import java.net.URI
import java.time.Instant
import scala.language.unsafeNulls

class SpatioTemporalDtoJsonTests extends AnyFunSpec with CpmetaJsonProtocol:

	describe("SpatioTemporalDto JSON format"):
		it("roundtrips variables as full URIs"):
			val dto = SpatioTemporalDto(
				title = "Spatial dataset",
				description = Some("desc"),
				spatial = URI("https://meta.icos-cp.eu/resources/latlonboxes/globalLatLonBox"),
				temporal = TemporalCoverage(
					interval = TimeInterval(
						start = Instant.parse("2024-01-01T00:00:00Z").nn,
						stop = Instant.parse("2024-12-31T23:59:59Z").nn
					),
					resolution = Some("hourly")
				),
				production = DataProductionDto(
					creator = URI("https://meta.icos-cp.eu/resources/organizations/ETC"),
					contributors = Seq.empty,
					hostOrganization = None,
					comment = None,
					sources = None,
					documentation = None,
					creationDate = Instant.parse("2025-01-01T00:00:00Z").nn
				),
				forStation = None,
				samplingHeight = None,
				customLandingPage = None,
				variables = Some(Seq(
					URI("https://meta.icos-cp.eu/resources/cpmeta/ET_T"),
					URI("https://meta.icos-cp.eu/resources/cpmeta/SWC_1_5_1")
				))
			)

			val json = dto.toJson
			val back = json.convertTo[SpatioTemporalDto]

			assert(back === dto)
			assert(
				json.asJsObject.fields("variables") === JsArray(
					JsString("https://meta.icos-cp.eu/resources/cpmeta/ET_T"),
					JsString("https://meta.icos-cp.eu/resources/cpmeta/SWC_1_5_1")
				)
			)
