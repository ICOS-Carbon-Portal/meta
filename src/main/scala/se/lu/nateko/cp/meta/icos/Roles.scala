package se.lu.nateko.cp.meta.icos

sealed trait Role{
	def name: String
}

object Role{
	val all: Seq[Role] = Seq(PI, Researcher, DataManager)
}

sealed abstract class NonPiRole(val name: String) extends Role

case object PI extends Role{def name = "PI"}
case object Researcher extends NonPiRole("Researcher")
case object DataManager extends NonPiRole("DataManager")
