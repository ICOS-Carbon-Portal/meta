package se.lu.nateko.cp.meta.icos

sealed trait Role{
	def name: String
}

object Role{
	//ATTENTION: Every new role must be added to the Role.all list
	val all: Seq[Role] = Seq(PI, Researcher, DataManager, Engineer, Administrator)
}

//ATTENTION: Every role must be listed in Role.all
sealed abstract class NonPiRole(val name: String) extends Role

case object PI extends Role{def name = "PI"}
case object Researcher extends NonPiRole("Researcher")
case object Engineer extends NonPiRole("Engineer")
case object DataManager extends NonPiRole("DataManager")
case object Administrator extends NonPiRole("Administrator")
