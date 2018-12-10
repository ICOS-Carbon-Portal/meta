package se.lu.nateko.cp.meta.icos

import java.time.Instant

import akka.stream.scaladsl.Source
import se.lu.nateko.cp.meta.core.data.Position

sealed trait OneOrMorePis

final case class SinglePi(one: Person) extends OneOrMorePis

final case class MoreThanOnePi(first: Person, second: Person, rest: Person*) extends OneOrMorePis{
	def all = first :: second :: rest.toList
}

case class Person(fname: String, lastName: String, email: Option[String])

sealed trait Role{
	def name: String
}

sealed abstract class NonPiRole(val name: String) extends Role

case object PI extends Role{def name = "PI"}
case object ResearcherAt extends NonPiRole("is researcher at")

sealed trait Organization{
	def cpId: String
	def tcId: String
	def name: String
}

trait Station extends Organization{
	def pos: Position
	def pi: OneOrMorePis
}

case class Institution(cpId: String, tcId: String, name: String) extends Organization

trait AssumedRole{
	def role: Role
	def holder: Person
	def org: Organization
}

case class Membership(role: AssumedRole, start: Option[Instant], stop: Option[Instant])

class State(val stations: Seq[Station], val roles: Seq[Membership])

trait TcMetaSource {

	type Pis <: OneOrMorePis

	case class TcStation(cpId: String, tcId: String, name: String, pos: Position, pi: Pis) extends Station

	case class TcAssumedRole(role: NonPiRole, holder: Person, org: Organization) extends AssumedRole

	class TcState(val stations: Seq[TcStation], val roles: Seq[TcAssumedRole])

	def state: Source[TcState, Any]
}
