package se.lu.nateko.cp.meta.icos

class RdfReader {

	def getCpOwnOrgs[T <: TC]: Seq[CompanyOrInstitution[T]] = ???

	def getCurrentState[T <: TC]: CpTcState[T] = ???

}
