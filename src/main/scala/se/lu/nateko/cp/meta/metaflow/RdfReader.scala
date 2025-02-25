package se.lu.nateko.cp.meta.metaflow

import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS}
import org.eclipse.rdf4j.model.{IRI, Statement, ValueFactory}
import se.lu.nateko.cp.meta.api.RdfLens.{CpLens, DocConn, DocLens, MetaConn, MetaLens}
import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.{EnvriConfigs, Funder}
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.instanceserver.StatementSource.*
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, RdfUpdate}
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.services.upload.DobjMetaReader
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.Validated.{CardinalityExpectation, validateSize}
import se.lu.nateko.cp.meta.utils.rdf4j.toRdf


class MetaflowLenses(val cpLens: CpLens, val envriLens: MetaLens, val docLens: DocLens)

class RdfReader(metaReader: DobjMetaReader, glob: InstanceServer, lenses: MetaflowLenses)(using EnvriConfigs):
	private val fetcher = new IcosMetaInstancesFetcher(metaReader)

	def getCpOwnOrgs[T <: TC : TcConf]: Validated[Seq[TcPlainOrg[T]]] = glob.access:
		given CpLens = lenses.cpLens
		fetcher.getPlainOrgs[T]

	def getCpOwnPeople[T <: TC : TcConf]: Validated[Seq[TcPerson[T]]] = glob.access:
		given CpLens = lenses.cpLens
		fetcher.getPeople[T]

	def getCurrentState[T <: TC : TcConf]: Validated[TcState[T]] = glob.access:
		given MetaLens = lenses.envriLens
		given DocLens = lenses.docLens
		fetcher.getCurrentState[T]

	def getTcUsages(iri: IRI): IndexedSeq[Statement] = glob.access:
		getStatements(null, null, iri)(using lenses.envriLens).map(stripContext).toIndexedSeq

	def getTcStatements(iri: IRI): IndexedSeq[Statement] = glob.access:
		getStatements(iri)(using lenses.envriLens).map(stripContext).toIndexedSeq

	def getCpStatements(iri: IRI): IndexedSeq[Statement] = glob.access:
		getStatements(iri, null, null)(using lenses.cpLens).map(stripContext).toIndexedSeq

	def keepMeaningful(updates: Seq[RdfUpdate]): IndexedSeq[RdfUpdate] = glob.access: glConn ?=>
		val conn: MetaConn = lenses.envriLens(using glConn)
		val (adds, dels) = updates.partition(_.isAssertion)
		val meaningfulAdds = adds.filterNot(u => conn.hasStatement(u.statement))
		val primConn = conn.primaryContextView
		val meaningfulDels = dels.filter(dupd => primConn.hasStatement(dupd.statement))
		(meaningfulDels ++ meaningfulAdds).toIndexedSeq

	private def stripContext(s: Statement) = glob.factory
		.createStatement(s.getSubject, s.getPredicate, s.getObject)
end RdfReader

private class IcosMetaInstancesFetcher(metaReader: DobjMetaReader)(using EnvriConfigs):
	//import metaReader.{vocab, metaVocab}
	val metaVocab = metaReader.metaVocab
	val vocab = metaReader.vocab
	private given factory: ValueFactory = metaVocab.factory

	def getCurrentState[T <: TC : TcConf](using MetaConn, DocConn): Validated[TcState[T]] = for
		stations <- getStations[T]
		memberships <- getMemberships
		instruments <- getInstruments
	yield
		TcState(stations, memberships, instruments)

	def getMemberships[T <: TC : TcConf](using MetaConn, DocConn): Validated[Seq[Membership[T]]] = {
		import CardinalityExpectation.AtMostOne
		val membOptSeqV = getDirectClassMembers(metaVocab.membershipClass).map: uri =>
			for
				orgOpt <- getOptTcOrgProp(uri, metaVocab.atOrganization)
				roleIri <- getSingleUri(uri, metaVocab.hasRole)
				persUris <- getPropValueHolders(metaVocab.hasMembership, uri)
					.validateSize(
						AtMostOne, s"Membership object $uri is assosiated with more than one person"
					)
				personOpt <- Validated.sinkOption:
					persUris.headOption.map: persUri =>
						getTcId[T](persUri).flatMap:
							getPerson(_, persUri)
				startOpt <- getOptionalInstant(uri, metaVocab.hasStartTime)
				endOpt <- getOptionalInstant(uri, metaVocab.hasEndTime)
				weight <- getOptionalInt(uri, metaVocab.hasAttributionWeight)
				extraInfo <- getOptionalString(uri, metaVocab.hasExtraRoleInfo)
			yield
				for org <- orgOpt; person <- personOpt yield
					val role = getRole(roleIri)
					val assumedRole = new AssumedRole(role, person, org, weight, extraInfo)
					Membership(UriId(uri), assumedRole, startOpt, endOpt)

		Validated.sequence(membOptSeqV).map(_.flatten)
	}


	def getInstruments[T <: TC : TcConf](using MetaConn, DocConn): Validated[Seq[TcInstrument[T]]] = getEntities[T, TcInstrument[T]](metaVocab.instrumentClass, true){
		(tcIdOpt, uri) => for
			model <- getSingleString(uri, metaVocab.hasModel)
			sn <- getSingleString(uri, metaVocab.hasSerialNumber)
			name <- getOptionalString(uri, metaVocab.hasName)
			comment <- getOptionalString(uri, RDFS.COMMENT)
			owner <- getOptTcOrgProp(uri, metaVocab.hasInstrumentOwner)
			vendor <- getOptTcOrgProp(uri, metaVocab.hasVendor)
			deployments <- Validated.sequence:
				getUriValues(uri, metaVocab.ssn.hasDeployment).map(getInstrDeployment[T])
		yield TcInstrument[T](
			tcId = tcIdOpt.getOrElse(throw new MetadataException(s"Instrument $uri had no TC id associated with it")),
			model = model,
			sn = sn,
			name = name,
			comment = comment,
			owner = owner,
			vendor = vendor,
			partsCpIds = getUriValues(uri, metaVocab.hasInstrumentComponent).map(UriId.apply),
			deployments = deployments
		)
	}

	private def getInstrDeployment[T <: TC : TcConf](iri: IRI)(using MetaConn): Validated[InstrumentDeployment[T]] =
		for
			stationIri <- getSingleUri(iri, metaVocab.atOrganization)
			instrPos <- metaReader.getInstrumentPosition(iri).optional
			stIdOpt <- getTcId[T](stationIri)
			variable <- getOptionalString(iri, metaVocab.hasVariableName)
			start <- getOptionalInstant(iri, metaVocab.hasStartTime)
			stop <- getOptionalInstant(iri, metaVocab.hasEndTime)
		yield InstrumentDeployment(
			cpId = UriId(iri),
			pos = instrPos,
			stationTcId = stIdOpt.getOrElse(throw new MetadataException(s"Station $stationIri had no TC id associated with it")),
			stationUriId = UriId(stationIri),
			variable = variable,
			start = start,
			stop = stop
		)


	def getStations[T <: TC](using conf: TcConf[T], mconn: MetaConn, dconn: DocConn): Validated[Seq[TcStation[T]]] =
		getEntities[T, TcStation[T]](conf.stationClass(metaVocab))(getTcStation)


	private def getTcStation[T <: TC : TcConf](tcIdOpt: Option[TcId[T]], uri: IRI)(using MetaConn, DocConn): Validated[TcStation[T]] =
		for
			coreStation <- metaReader.getStation(uri)
			respOrg <- Validated
				.sinkOption:
					coreStation.responsibleOrganization.map: ro =>
						getTcOrganization[T](ro.self.uri.toRdf)
				.map(_.flatten)
			funding <- Validated.sequence:
				coreStation.funding.toSeq.flatten.map: coref =>
					makeTcFunder(coref.funder).map: tcFunder =>
						TcFunding[T](UriId(coref.self.uri), tcFunder, coref)
		yield TcStation(
			cpId = UriId(uri),
			tcId = tcIdOpt.getOrElse(throw new MetadataException(s"Station $uri had no TC id associated with it")),
			core = coreStation,
			responsibleOrg = respOrg.collect{case org: TcPlainOrg[T] => org},
			funding = funding
		)


	private def getGenericOrg[T <: TC](tcId: Option[TcId[T]], uri: IRI)(using MetaConn): Validated[TcGenericOrg[T]] =
		metaReader.getOrganization(uri).map: core =>
			TcGenericOrg[T](UriId(uri), tcId, core)


	private def getTcFunder[T <: TC](tcId: Option[TcId[T]], uri: IRI)(using MetaConn): Validated[TcFunder[T]] =
		metaReader.getFunder(uri).map:
			TcFunder[T](UriId(uri), tcId, _)


	private def makeTcFunder[T <: TC : TcConf](core: Funder)(using MetaConn): Validated[TcFunder[T]] =
		val uri = core.org.self.uri.toRdf
		getTcId(uri).map:
			TcFunder[T](UriId(uri), _, core)


	private def getRole(iri: IRI): Role =
		val roleId = UriId(iri).urlSafeString
		Role.forName(roleId).getOrElse(throw new Exception(s"Unrecognized role: $roleId"))


	private def getPerson[T <: TC](tcId: Option[TcId[T]], uri: IRI)(using MetaConn): Validated[TcPerson[T]] =
		metaReader.getPerson(uri).map: core =>
			TcPerson[T](UriId(uri), tcId, core.firstName, core.lastName, core.email, core.orcid)

	private def getOptTcOrgProp[T <: TC : TcConf](uri: IRI, pred: IRI)(using MetaConn, DocConn): Validated[Option[TcOrg[T]]] =
		getOptionalUri(uri, pred).flatMap: uriOpt =>
			Validated.sinkOption(uriOpt.map(getTcOrganization)).map(_.flatten)


	private def getTcOrganization[T <: TC : TcConf](uri: IRI)(using MetaConn, DocConn): Validated[Option[TcOrg[T]]] =

		if resourceHasType(uri, stationClass) then
			for stId <- getTcId(uri); station <- getTcStation(stId, uri) yield Some(station)

		else if resourceHasType(uri, metaVocab.orgClass) then
			for orgId <- getTcId(uri); org <- getGenericOrg(orgId, uri) yield Some(org)

		else if resourceHasType(uri, metaVocab.funderClass) then
			for funderId <- getTcId(uri); funder <- getTcFunder(funderId, uri) yield Some(funder)

		else
			Validated.ok(None) //uri is neither a TC-specific station nor a plain organization


	def getPeople[T <: TC : TcConf](using MetaConn): Validated[Seq[TcPerson[T]]] = getEntities[T, TcPerson[T]](metaVocab.personClass)(getPerson)


	def getPlainOrgs[T <: TC : TcConf](using MetaConn): Validated[Seq[TcPlainOrg[T]]] = for(
		gen <- getEntities[T, TcGenericOrg[T]](metaVocab.orgClass)(getGenericOrg);
		fund <- getEntities[T, TcFunder[T]](metaVocab.funderClass)(getTcFunder)
	) yield gen ++ fund


	private def getEntities[T <: TC : TcConf, E](
		cls: IRI, requireTcId: Boolean = false
	)(make: (Option[TcId[T]], IRI) => MetaConn ?=> Validated[E]): MetaConn ?=> Validated[Seq[E]] =
		val seqV = getDirectClassMembers(cls).map: uri =>
			for
				tcIdOpt <- getTcId(uri) if !requireTcId || tcIdOpt.isDefined
				entity <- make(tcIdOpt, uri)
			yield entity
		Validated.sequence(seqV)


	protected def getTcId[T <: TC](uri: IRI)(using tcConf: TcConf[T], conn: StatementSource): Validated[Option[TcId[T]]] =
		val tcIdPred = tcConf.tcIdPredicate(metaVocab)
		getOptionalString(uri, tcIdPred).map(_.map(tcConf.makeId))


	private def stationClass[T <: TC](using tcConf: TcConf[T]): IRI = tcConf.stationClass(metaVocab)

	private def getDirectClassMembers(cls: IRI)(using StatementSource): IndexedSeq[IRI] = getPropValueHolders(RDF.TYPE, cls)

end IcosMetaInstancesFetcher
