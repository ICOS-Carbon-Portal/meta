package se.lu.nateko.cp.meta.icos

class RdfReader {

	def getCpOwnOrgs[T <: TC]: Seq[CompanyOrInstitution[T]] = ???

	def getCpOwnPeople[T <: TC]: Seq[Person[T]] = ???

	def getCurrentState[T <: TC]: CpTcState[T] = ???

}
