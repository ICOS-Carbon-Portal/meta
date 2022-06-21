package se.lu.nateko.cp.meta.api

import se.lu.nateko.cp.meta.core.data.FeatureCollection
import se.lu.nateko.cp.meta.core.data.GeoFeature
import se.lu.nateko.cp.meta.core.data.Person
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.core.data.Station
import se.lu.nateko.cp.meta.icos.Role
import se.lu.nateko.cp.meta.services.citation.AttributionProvider
import spray.json.*

import java.time.Instant

import AttributionProvider.{*, given}


class OrganizationExtra[+O](val org: O, val staff: Seq[Membership]){
	val (currentStaff, formerStaff) = {
		val now = Instant.now()
		staff.sortBy(_.person).partition(m => m.role.end.fold(true)(end => end.isAfter(now)))
	}
}

class PersonExtra(val person: Person, val roles: Seq[PersonRole]){
	def sortedRoles = roles.sorted
}

object OrganizationExtra{
	import DefaultJsonProtocol.*
	import se.lu.nateko.cp.meta.core.data.JsonSupport.given

	given JsonFormat[Role] with{

		override def read(json: JsValue): Role = json match{
			case JsString(name) => Role.forName(name).getOrElse(
				deserializationError(s"No Role with name $name")
			)
			case _ =>
				deserializationError("Expected JsString representing a Role, got " + json.compactPrint)
		}

		override def write(role: Role): JsValue = JsString(role.name)

	}

	given RootJsonFormat[RoleDetails] = jsonFormat5(RoleDetails.apply)
	given RootJsonFormat[Membership] = jsonFormat2(Membership.apply)
	given RootJsonFormat[PersonRole] = jsonFormat2(PersonRole.apply)

	given [O : JsonWriter]: JsonWriter[OrganizationExtra[O]] with{
		override def write(oe: OrganizationExtra[O]): JsValue = {
			val core = oe.org.toJson.asJsObject
			val allFields = core.fields + ("staff" -> oe.staff.toJson)
			JsObject(allFields)
		}
	}

	given persExtraWriter: JsonWriter[PersonExtra] with{
		override def write(pe: PersonExtra): JsValue = {
			val core = pe.person.toJson.asJsObject
			val allFields = core.fields + ("roles" -> pe.roles.toJson)
			JsObject(allFields)
		}
	}
}
