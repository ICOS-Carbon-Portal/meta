package se.lu.nateko.cp.meta.icos

import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.upload.CpmetaFetcher
import se.lu.nateko.cp.meta.core.data
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.IRI
import scala.reflect.ClassTag
import se.lu.nateko.cp.meta.core.data.Position

class RdfReader(cpInsts: InstanceServer, tcInsts: InstanceServer)(implicit envriConfigs: EnvriConfigs) {

	private val cpOwnMetasFetcher = new IcosMetaInstancesFetcher(cpInsts)
	private val tcMetasFetcher = new IcosMetaInstancesFetcher(tcInsts)

	def getCpOwnOrgs[T <: TC : TcConf]: Seq[CompanyOrInstitution[T]] = cpOwnMetasFetcher.getOrgs[T]

	def getCpOwnPeople[T <: TC : TcConf]: Seq[Person[T]] = cpOwnMetasFetcher.getPeople[T]

	def getCurrentState[T <: TC : TcConf]: CpTcState[T] = tcMetasFetcher.getCurrentState[T]

}

private class IcosMetaInstancesFetcher(val server: InstanceServer)(implicit envriConfigs: EnvriConfigs) extends CpmetaFetcher{
	val vocab = new CpVocab(server.factory)

	def getCurrentState[T <: TC : TcConf]: CpTcState[T] = {
		???
	}

	def getInstruments[T <: TC : TcConf]: Seq[Instrument[T]] = getEntities[T, Instrument[T]](metaVocab.instrumentClass){
		(tcId, uri) => Instrument[T](
			cpId = uri.getLocalName,
			tcId = tcId,
			model = getSingleString(uri, metaVocab.hasModel),
			sn = getSingleString(uri, metaVocab.hasSerialNumber),
			name = getOptionalString(uri, metaVocab.hasName),
			owner = getOptionalUri(uri, metaVocab.hasInstrumentOwner).map(uri => getOrganization(uri)),
			vendor = getOptionalUri(uri, metaVocab.hasVendor).map(uri => getOrganization(uri)),
			partsCpIds = server.getUriValues(uri, metaVocab.dcterms.hasPart).map(_.getLocalName)
		)
	}

	def getStations[T <: TC](implicit conf: TcConf[T]): Seq[CpStation[T]] =
		getEntities[T, CpStation[T]](conf.stationClass(metaVocab))(getStation)

	private def getStation[T <: TC : TcConf](tcId: TcId[T], uri: IRI): CpStation[T] = {
		val id = getSingleString(uri, metaVocab.hasStationId)
		val name = getSingleString(uri, metaVocab.hasName)

		val latOpt = getOptionalDouble(uri, metaVocab.hasLatitude)
		val lonOpt = getOptionalDouble(uri, metaVocab.hasLongitude)

		val stationaryOpt = for(lat <- latOpt; lon <- lonOpt) yield {
			val altOpt = getOptionalFloat(uri, metaVocab.hasElevation)
			CpStationaryStation(uri.getLocalName, tcId, name, id, Position(lat, lon, altOpt))
		}

		//TODO Add json reading
		stationaryOpt.getOrElse(CpMobileStation(uri.getLocalName, tcId, name, id, None))
	}

	def getOrganization[T <: TC : TcConf](uri: IRI): Organization[T] = ???

	def getPeople[T <: TC : TcConf]: Seq[Person[T]] = getEntities[T, Person[T]](metaVocab.personClass){
		(tcId, uri) =>
			val core: data.Person = getPerson(uri)
			val email = getOptionalString(uri, metaVocab.hasEmail)
			Person[T](uri.getLocalName, tcId, core.firstName, core.lastName, email)
	}

	def getOrgs[T <: TC : TcConf]: Seq[CompanyOrInstitution[T]] = getEntities[T, CompanyOrInstitution[T]](metaVocab.orgClass){
		(tcId, uri) =>
			val core: data.Organization = getOrganization(uri)
			CompanyOrInstitution[T](uri.getLocalName, tcId, core.name, core.self.label)
	}

	private def getEntities[T <: TC : TcConf, E](cls: IRI)(make: (TcId[T], IRI) => E): Seq[E] = {

		val tcConf = implicitly[TcConf[T]]
		val tcIdPred = tcConf.tcIdPredicate(metaVocab)

		server
			.getStatements(None, Some(RDF.TYPE), Some(cls))
			.map(_.getObject)
			.collect{case iri: IRI => iri}
			.flatMap{uri =>
				for(tcid <- getOptionalString(uri, tcIdPred)) yield make(tcConf.makeId(tcid), uri)
			}.toIndexedSeq
	}
}
