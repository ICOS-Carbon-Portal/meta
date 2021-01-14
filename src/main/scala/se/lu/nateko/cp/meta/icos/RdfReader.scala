package se.lu.nateko.cp.meta.icos

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.RDF

import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import se.lu.nateko.cp.meta.core.data.Orcid
import se.lu.nateko.cp.meta.core.data.Position
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.services.upload.DobjMetaFetcher
import se.lu.nateko.cp.meta.services.upload.PlainStaticObjectFetcher
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.utils.Validated

class RdfReader(cpInsts: InstanceServer, tcInsts: InstanceServer, allObjs: InstanceServer)(implicit envriConfigs: EnvriConfigs) {

	private[this] val plainFetcher = new PlainStaticObjectFetcher(allObjs)
	private[this] val cpOwnMetasFetcher = new IcosMetaInstancesFetcher(cpInsts, plainFetcher)
	private[this] val tcMetasFetcher = new IcosMetaInstancesFetcher(tcInsts, plainFetcher)

	def getCpOwnOrgs[T <: TC : TcConf]: Validated[Seq[CompanyOrInstitution[T]]] = cpOwnMetasFetcher.getOrgs[T]

	def getCpOwnPeople[T <: TC : TcConf]: Validated[Seq[Person[T]]] = cpOwnMetasFetcher.getPeople[T]

	def getCurrentState[T <: TC : TcConf]: Validated[TcState[T]] = tcMetasFetcher.getCurrentState[T]

	def getTcOnlyUsages(iri: IRI): IndexedSeq[Statement] = minus(
		tcInsts.getStatements(None, None, Some(iri)).map(stripContext),
		cpInsts.getStatements(None, None, Some(iri)).map(stripContext)
	)

	def getTcOnlyStatements(iri: IRI): IndexedSeq[Statement] = minus(
		tcInsts.getStatements(Some(iri), None, None).map(stripContext),
		getCpStatements(iri)
	)

	def getCpStatements(iri: IRI): Iterator[Statement] =
		cpInsts.getStatements(Some(iri), None, None).map(stripContext)

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

private class IcosMetaInstancesFetcher(
	val server: InstanceServer,
	val plainObjFetcher: PlainStaticObjectFetcher
)(implicit envriConfigs: EnvriConfigs) extends DobjMetaFetcher{
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
					val persons = getPropValueHolders(metaVocab.hasMembership, uri).map{persUri =>
						getPerson(getTcId[T](persUri), persUri)
					}.toIndexedSeq
					assert(persons.size <= 1, s"Membership object $uri is assosiated with ${persons.size} people, which is illegal")
					persons.headOption
				}
			) yield{
				val startOpt = getOptionalInstant(uri, metaVocab.hasStartTime)
				val endOpt = getOptionalInstant(uri, metaVocab.hasEndTime)
				val weight = getOptionalInt(uri, metaVocab.hasAttributionWeight)
				val extraInfo = getOptionalString(uri, metaVocab.hasExtraRoleInfo)
				val assumedRole = new AssumedRole(role, person, org, weight, extraInfo)
				Membership(UriId(uri), assumedRole, startOpt, endOpt)
			})
		}
		Validated.sequence(membOptSeqV).map(_.flatten)
	}


	def getInstruments[T <: TC : TcConf]: Validated[Seq[Instrument[T]]] = getEntities[T, Instrument[T]](metaVocab.instrumentClass, true){
		(tcIdOpt, uri) => Instrument[T](
			tcId = tcIdOpt.getOrElse(throw new MetadataException(s"Instrument $uri had no TC id associated with it")),
			model = getSingleString(uri, metaVocab.hasModel),
			sn = getSingleString(uri, metaVocab.hasSerialNumber),
			name = getOptionalString(uri, metaVocab.hasName),
			owner = getOptionalUri(uri, metaVocab.hasInstrumentOwner).flatMap(o => getOrganization(o)),
			vendor = getOptionalUri(uri, metaVocab.hasVendor).flatMap(v => getOrganization(v)),
			partsCpIds = server.getUriValues(uri, metaVocab.hasInstrumentComponent).map(UriId.apply)
		)
	}


	def getStations[T <: TC](implicit conf: TcConf[T]): Validated[Seq[TcStation[T]]] =
		getEntities[T, TcStation[T]](conf.stationClass(metaVocab))(getTcStation)


	private def getTcStation[T <: TC : TcConf](tcIdOpt: Option[TcId[T]], uri: IRI): TcStation[T] = {
		TcStation(
			cpId = UriId(uri),
			tcId = tcIdOpt.getOrElse(throw new MetadataException(s"Station $uri had no TC id associated with it")),
			core = getStation(uri)
		)
	}


	private def getCompOrInst[T <: TC](tcId: Option[TcId[T]], uri: IRI): CompanyOrInstitution[T] = {
		val core: data.Organization = getOrganization(uri)
		CompanyOrInstitution[T](UriId(uri), tcId, core.name, core.self.label)
	}

	private def getRole(iri: IRI): Role = {
		val roleId = UriId(iri).urlSafeString
		Role.forName(roleId).getOrElse(throw new Exception(s"Unrecognized role: $roleId"))
	}

	private def getPerson[T <: TC](tcId: Option[TcId[T]], uri: IRI): Person[T] = {
		val core: data.Person = getPerson(uri)
		val email = getOptionalString(uri, metaVocab.hasEmail)
		val orcid = getOptionalString(uri, metaVocab.hasOrcidId).flatMap(Orcid.unapply)
		Person[T](UriId(uri), tcId, core.firstName, core.lastName, email, orcid)
	}

	private def getOrganization[T <: TC : TcConf](uri: IRI): Option[Organization[T]] =
		if(server.hasStatement(uri, RDF.TYPE, stationClass))
			Some(getTcStation(getTcId(uri), uri))
		else if(server.hasStatement(uri, RDF.TYPE, metaVocab.orgClass))
			Some(getCompOrInst(getTcId(uri), uri))
		else
			None //uri is neither a TC-specific station nor a plain organization


	def getPeople[T <: TC : TcConf]: Validated[Seq[Person[T]]] = getEntities[T, Person[T]](metaVocab.personClass)(getPerson)


	def getOrgs[T <: TC : TcConf]: Validated[Seq[CompanyOrInstitution[T]]] =
		getEntities[T, CompanyOrInstitution[T]](metaVocab.orgClass)(getCompOrInst)


	private def getEntities[T <: TC : TcConf, E](cls: IRI, requireTcId: Boolean = false)(make: (Option[TcId[T]], IRI) => E): Validated[Seq[E]] = {
		val seqV = for(
			uri <- getDirectClassMembers(cls);
			tcIdOpt = getTcId(uri)
			if !requireTcId || tcIdOpt.isDefined
		) yield Validated(make(tcIdOpt, uri))
		Validated.sequence(seqV)
	}


	protected def getTcId[T <: TC](uri: IRI)(implicit tcConf: TcConf[T]): Option[TcId[T]] = {
		val tcIdPred = tcConf.tcIdPredicate(metaVocab)
		getOptionalString(uri, tcIdPred).map(tcConf.makeId)
	}


	private def stationClass[T <: TC](implicit tcConf: TcConf[T]): IRI = tcConf.stationClass(metaVocab)


	private def getDirectClassMembers(cls: IRI): IndexedSeq[IRI] = getPropValueHolders(RDF.TYPE, cls)

	private def getPropValueHolders(prop: IRI, v: Value): IndexedSeq[IRI] = server
		.getStatements(None, Some(prop), Some(v))
		.map(_.getSubject)
		.collect{case iri: IRI => iri}
		.toIndexedSeq

}
