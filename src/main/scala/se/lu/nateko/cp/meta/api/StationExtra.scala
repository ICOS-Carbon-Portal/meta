package se.lu.nateko.cp.meta.api

import se.lu.nateko.cp.meta.services.citation.AttributionProvider
import spray.json._
import se.lu.nateko.cp.meta.core.data.FeatureCollection
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.data.Station
import se.lu.nateko.cp.meta.icos.Role
import java.time.Instant
import AttributionProvider.{Membership, personOrdering}


class StationExtra(val station: Station, val staff: Seq[Membership]){
	val (currentStaff, formerStaff) = {
		val now = Instant.now()
		staff.sortBy(_.person).partition(m => m.end.fold(true)(end => end.isAfter(now)))
	}
}

object StationExtra extends DefaultJsonProtocol{
	import se.lu.nateko.cp.meta.core.data.JsonSupport._

	implicit object roleFormat extends JsonFormat[Role]{

		override def read(json: JsValue): Role = json match{
			case JsString(name) => Role.forName(name).getOrElse(
				deserializationError(s"No Role with name $name")
			)
			case _ =>
				deserializationError("Expected JsString representing a Role, got " + json.compactPrint)
		}

		override def write(role: Role): JsValue = JsString(role.name)

	}

	implicit val membershipFormat = jsonFormat6(Membership)

	implicit object stationExtraWriter extends JsonWriter[StationExtra]{
		override def write(se: StationExtra): JsValue = {
			val core = se.station.toJson.asJsObject
			val allFields = core.fields + ("staff" -> se.staff.toJson)
			JsObject(allFields)
		}
	}
}