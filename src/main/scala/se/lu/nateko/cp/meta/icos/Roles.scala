package se.lu.nateko.cp.meta.icos

sealed trait Role{
	//ATTENTION name must be URL-friendly, it is a technical id, not user-facing label
	def name: String
	override def toString = name
}

object Role{
	//ATTENTION: Every new role must be added to the Role.all list
	val all: Seq[Role] = Seq(PI, Researcher, DataManager, Engineer, Administrator)
	def forName(name: String): Option[Role] = all.find(_.name == name)
}

//ATTENTION: Every role must be listed in Role.all
sealed abstract class NonPiRole(val name: String) extends Role

case object PI extends Role{def name = "PI"}
case object Researcher extends NonPiRole("Researcher")
case object Engineer extends NonPiRole("Engineer")
case object DataManager extends NonPiRole("DataManager")
case object Administrator extends NonPiRole("Administrator")
