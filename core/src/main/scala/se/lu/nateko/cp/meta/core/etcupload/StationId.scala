package se.lu.nateko.cp.meta.core.etcupload

import scala.language.unsafeNulls

import java.util.regex.Pattern

class StationId private (val id: String){

	override def equals(other: Any) = other match{
		case otherId: StationId => otherId.id == id
		case _ => false
	}

	override def hashCode: Int = id.hashCode

	override def toString = s"StationId($id)"
}

object StationId{

	private val pattern = Pattern.compile("[A-Z]{2}\\-[A-Za-z][A-Za-z0-9]{2}")

	def unapply(s: String): Option[StationId] =
		if(pattern.matcher(s).matches) Some(new StationId(s))
		else None

	def unapply(id: StationId): Option[String] = Some(id.id)
}
