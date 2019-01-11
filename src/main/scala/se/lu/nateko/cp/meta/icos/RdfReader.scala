package se.lu.nateko.cp.meta.icos

import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.upload.CpmetaFetcher
import se.lu.nateko.cp.meta.core.data
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.IRI
import scala.reflect.ClassTag

class RdfReader(cpInsts: InstanceServer)(implicit envriConfigs: EnvriConfigs) {

	private val cpOwnMetasFetcher = new IcosMetaInstancesFetcher(cpInsts)

	def getCpOwnOrgs[T <: TC]: Seq[CompanyOrInstitution[T]] = cpOwnMetasFetcher.getOrgs[T]

	def getCpOwnPeople[T <: TC : TcConf]: Seq[Person[T]] = cpOwnMetasFetcher.getPeople[T]

	def getCurrentState[T <: TC]: CpTcState[T] = ???

}

private class IcosMetaInstancesFetcher(val server: InstanceServer)(implicit envriConfigs: EnvriConfigs) extends CpmetaFetcher{
	val vocab = new CpVocab(server.factory)

	def getPeople[T <: TC : TcConf]: Seq[Person[T]] = {

		val tcConf = implicitly[TcConf[T]]
		val tcIdPred = tcConf.tcIdPredicate(metaVocab)

		server.getStatements(None, Some(RDF.TYPE), Some(metaVocab.personClass))
			.map(_.getObject)
			.collect{case iri: IRI => iri}
			.flatMap{uri =>
				val core: data.Person = getPerson(uri)

				val email = getOptionalString(uri, metaVocab.hasEmail)

				for(tcid <- getOptionalString(uri, tcIdPred)) yield
					Person[T](uri.getLocalName, tcConf.makeId(tcid), core.firstName, core.lastName, email)
			}
			.toIndexedSeq
	}

	def getOrgs[T <: TC]: Seq[CompanyOrInstitution[T]] = ???
}
