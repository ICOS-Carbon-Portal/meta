package se.lu.nateko.cp.meta.icos

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.RDF

import se.lu.nateko.cp.meta.core.data
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.services.upload.CpmetaFetcher
import org.eclipse.rdf4j.model.Statement
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.utils.Validated

class RdfReader(cpInsts: InstanceServer, tcInsts: InstanceServer)(implicit envriConfigs: EnvriConfigs) {

	private val cpOwnMetasFetcher = new IcosMetaInstancesFetcher(cpInsts)
	private val tcMetasFetcher = new IcosMetaInstancesFetcher(tcInsts)

	def getCpOwnOrgs[T <: TC : TcConf]: Validated[Seq[CompanyOrInstitution[T]]] = cpOwnMetasFetcher.getOrgs[T]

	def getCpOwnPeople[T <: TC : TcConf]: Validated[Seq[Person[T]]] = cpOwnMetasFetcher.getPeople[T]

	def getCurrentState[T <: TC : TcConf]: Validated[TcState[T]] = tcMetasFetcher.getCurrentState[T]

	def getTcOnlyUsages(iri: IRI): IndexedSeq[Statement] = minus(
		tcInsts.getStatements(None, None, Some(iri)).map(stripContext),
		cpInsts.getStatements(None, None, Some(iri)).map(stripContext)
	)

	def getTcOnlyStatements(iri: IRI): IndexedSeq[Statement] = minus(
		tcInsts.getStatements(Some(iri), None, None).map(stripContext),
		cpInsts.getStatements(Some(iri), None, None).map(stripContext)
	)

	def keepMeaningful(updates: Seq[RdfUpdate]): Seq[RdfUpdate] = {
		val (adds, dels) = updates.partition(_.isAssertion)
		val meaningfulAdds = tcInsts.filterNotContainedStatements(adds.map(_.statement)).map(RdfUpdate(_, true))
		val delStats = dels.map(_.statement)
		val uselessDelStats = tcInsts.writeContextsView.filterNotContainedStatements(delStats)
		val meaningfulDels = minus(delStats.iterator, uselessDelStats.iterator).map(RdfUpdate(_, false))
		meaningfulDels ++ meaningfulAdds
	}

	private def minus(takeFrom: Iterator[Statement], takeAway: Iterator[Statement]): IndexedSeq[Statement] =
		takeFrom.toSet.diff(takeAway.toSet).toIndexedSeq

	private def stripContext(s: Statement) = tcInsts.factory
		.createStatement(s.getSubject, s.getPredicate, s.getObject)
}

private class IcosMetaInstancesFetcher(val server: InstanceServer)(implicit envriConfigs: EnvriConfigs) extends CpmetaFetcher{
	val vocab = new CpVocab(server.factory)

	def getCurrentState[T <: TC : TcConf]: Validated[TcState[T]] = for(
		stations <- getStations[T];
		memberships <- getMemberships;
		instruments <- getInstruments
	) yield
		new TcState(stations, memberships, instruments)

	def getMemberships[T <: TC : TcConf]: Validated[Seq[Membership[T]]] = {
		val membOptSeqV = getDirectClassMembers(metaVocab.membershipClass).map{uri =>
			Validated(for(
				orgUri <- getOptionalUri(uri, metaVocab.atOrganization);
				org <- getOrganization(orgUri);
				role = getRole(getSingleUri(uri, metaVocab.hasRole));
				person <- {
					val persons = getPropValueHolders(metaVocab.hasMembership, uri).flatMap{persUri =>
						getTcId[T](persUri).map(getPerson(_, persUri))
					}.toIndexedSeq
					assert(persons.size <= 1, s"Membership object $uri is assosiated with ${persons.size} people, which is illegal")
					persons.headOption
				}
			) yield{
				val startOpt = getOptionalInstant(uri, metaVocab.hasStartTime)
				val endOpt = getOptionalInstant(uri, metaVocab.hasEndTime)
				val weight = getOptionalInt(uri, metaVocab.hasAttributionWeight)
				val assumedRole = new AssumedRole(role, person, org, weight)
				Membership(uri.getLocalName, assumedRole, startOpt, endOpt)
			})
		}
		Validated.sequence(membOptSeqV).map(_.flatten)
	}


	def getInstruments[T <: TC : TcConf]: Validated[Seq[Instrument[T]]] = getEntities[T, Instrument[T]](metaVocab.instrumentClass){
		(tcId, uri) => Instrument[T](
			cpId = uri.getLocalName,
			tcId = tcId,
			model = getSingleString(uri, metaVocab.hasModel),
			sn = getSingleString(uri, metaVocab.hasSerialNumber),
			name = getOptionalString(uri, metaVocab.hasName),
			owner = getOptionalUri(uri, metaVocab.hasInstrumentOwner).flatMap(uri => getOrganization(uri)),
			vendor = getOptionalUri(uri, metaVocab.hasVendor).flatMap(uri => getOrganization(uri)),
			partsCpIds = server.getUriValues(uri, metaVocab.dcterms.hasPart).map(_.getLocalName)
		)
	}


	def getStations[T <: TC](implicit conf: TcConf[T]): Validated[Seq[CpStation[T]]] =
		getEntities[T, CpStation[T]](conf.stationClass(metaVocab))(getStation)


	private def getStation[T <: TC : TcConf](tcId: TcId[T], uri: IRI): CpStation[T] = {

		val id = getSingleString(uri, metaVocab.hasStationId)
		val cpId = uri.getLocalName
		val name = getSingleString(uri, metaVocab.hasName)

		val latOpt = getOptionalDouble(uri, metaVocab.hasLatitude)
		val lonOpt = getOptionalDouble(uri, metaVocab.hasLongitude)

		val countryOpt = getOptionalString(uri, metaVocab.countryCode).flatMap(CountryCode.unapply)

		val stationaryOpt = for(lat <- latOpt; lon <- lonOpt) yield {
			val altOpt = getOptionalFloat(uri, metaVocab.hasElevation)
			CpStationaryStation(cpId, tcId, name, id, countryOpt, Position(lat, lon, altOpt))
		}

		stationaryOpt.getOrElse{
			val jsonOpt = getOptionalUri(uri, metaVocab.hasSpatialCoverage)
				.flatMap(getOptionalString(_, metaVocab.asGeoJSON))
			CpMobileStation(cpId, tcId, name, id, countryOpt, jsonOpt)
		}
	}


	private def getCompOrInst[T <: TC](tcId: TcId[T], uri: IRI): CompanyOrInstitution[T] = {
		val core: data.Organization = getOrganization(uri)
		CompanyOrInstitution[T](vocab.getOrganizationId(uri), tcId, core.name, core.self.label)
	}

	protected def getRole(iri: IRI): Role = {
		val roleId = iri.getLocalName
		Role.all.find(_.name == roleId).getOrElse(throw new Exception(s"Unrecognized role: $roleId"))
	}

	protected def getPerson[T <: TC](tcId: TcId[T], uri: IRI): Person[T] = {
		val core: data.Person = getPerson(uri)
		val email = getOptionalString(uri, metaVocab.hasEmail)
		Person[T](uri.getLocalName, tcId, core.firstName, core.lastName, email)
	}

	def getOrganization[T <: TC : TcConf](uri: IRI): Option[Organization[T]] = getTcId(uri).map{tcId =>

		if(server.hasStatement(uri, RDF.TYPE, stationClass))
			getStation(tcId, uri)
		else if(server.hasStatement(uri, RDF.TYPE, metaVocab.orgClass))
			getCompOrInst(tcId, uri)
		else
			throw new MetadataException(s"$uri is neither a station nor a plain organization")
	}


	def getPeople[T <: TC : TcConf]: Validated[Seq[Person[T]]] = getEntities[T, Person[T]](metaVocab.personClass)(getPerson)


	def getOrgs[T <: TC : TcConf]: Validated[Seq[CompanyOrInstitution[T]]] =
		getEntities[T, CompanyOrInstitution[T]](metaVocab.orgClass)(getCompOrInst)


	private def getEntities[T <: TC : TcConf, E](cls: IRI)(make: (TcId[T], IRI) => E): Validated[Seq[E]] = {
		val seqV = for(
			uri <- getDirectClassMembers(cls);
			tcId <- getTcId(uri)
		) yield Validated(make(tcId, uri))
		Validated.sequence(seqV)
	}


	protected def getTcId[T <: TC](uri: IRI)(implicit tcConf: TcConf[T]): Option[TcId[T]] = {
		val tcIdPred = tcConf.tcIdPredicate(metaVocab)
		getOptionalString(uri, tcIdPred).map(tcConf.makeId)
	}


	private def stationClass[T <: TC](implicit tcConf: TcConf[T]): IRI = tcConf.stationClass(metaVocab)


	protected def getDirectClassMembers(cls: IRI): Iterator[IRI] = getPropValueHolders(RDF.TYPE, cls)

	private def getPropValueHolders(prop: IRI, v: Value): Iterator[IRI] = server
		.getStatements(None, Some(prop), Some(v))
		.map(_.getSubject)
		.collect{case iri: IRI => iri}

}
