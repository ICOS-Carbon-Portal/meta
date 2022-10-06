package se.lu.nateko.cp.meta.services.upload

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.unmarshalling.Unmarshal
import se.lu.nateko.cp.doi.CoolDoi
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.doi.DoiMeta
import se.lu.nateko.cp.doi.core.DoiClient
import se.lu.nateko.cp.doi.core.DoiClientConfig
import se.lu.nateko.cp.doi.core.PlainJavaDoiHttp
import se.lu.nateko.cp.doi.meta.GenericName
import se.lu.nateko.cp.doi.meta.*
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.SpatioTemporalDto
import se.lu.nateko.cp.meta.core.data.Agent
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.DataProduction
import se.lu.nateko.cp.meta.core.data.DocObject
import se.lu.nateko.cp.meta.core.data.Envri
import se.lu.nateko.cp.meta.core.data.FeatureCollection
import se.lu.nateko.cp.meta.core.data.FunderIdType
import se.lu.nateko.cp.meta.core.data.Funding
import se.lu.nateko.cp.meta.core.data.Organization
import se.lu.nateko.cp.meta.core.data.Person
import se.lu.nateko.cp.meta.core.data.PlainStaticObject
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer

import java.net.URI
import java.time.Year
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import java.time.Instant

class DoiService(conf: CpmetaConfig, fetcher: UriSerializer)(implicit ctxt: ExecutionContext) {

	private def client(implicit envri: Envri): DoiClient = {
		val doiConf = conf.doi(envri)
		val http = new PlainJavaDoiHttp(doiConf.symbol, doiConf.password)
		val clientConf = new DoiClientConfig(doiConf.symbol, doiConf.password, doiConf.restEndpoint.toURL(), doiConf.prefix)
		new DoiClient(clientConf, http)
	}

	private val ccby4 = Rights("CC BY 4.0", Some("https://creativecommons.org/licenses/by/4.0"))

	private def saveDoi(meta: DoiMeta)(implicit envri: Envri): Future[Doi] =
		client.putMetadata(meta).map(_ => meta.doi)

	def createDraftDoi(dataItemLandingPage: URI)(implicit envri: Envri): Future[Option[Doi]] = {
		import UriSerializer.Hash
		val uri = Uri(dataItemLandingPage.toString)

		val doiMetaOpt: Option[DoiMeta] =
			(uri.path match {
				case Hash.Collection(_)  => fetcher.fetchStaticCollection(uri)
					.map(makeCollectionDoi)

				case Hash.Object(_)      => fetcher.fetchStaticObject(uri)
					.map{
						case data: DataObject => makeDataObjectDoi(data)
						case doc: DocObject   => makeDocObjectDoi(doc)
					}
				case _ => None
			})
			.map(_.copy(url = Some(dataItemLandingPage.toString)))

		doiMetaOpt.fold(Future.successful(Option.empty[Doi]))(m => saveDoi(m).map(Some(_)))
	}

	def makeDataObjectDoi(dobj: DataObject)(using Envri): DoiMeta = {
		val creators = dobj.references.authors.fold(Seq.empty[Creator])(_.map(toDoiCreator))
		val plainContributors = dobj.specificInfo.fold(
			l3 => l3.productionInfo.contributors.map(toDoiContributor),
			_ => Seq()
		)
		val licence: Rights = dobj.references.licence.fold(ccby4)(lic => Rights(lic.name, Some(lic.url.toString)))
		val pubYear = dobj.submission.stop.getOrElse(dobj.submission.start).toString.take(4).toInt

		DoiMeta(
			doi = client.doi(CoolDoi.makeRandom),
			creators = creators,
			titles = dobj.references.title.map(t => Seq(Title(t, None, None))),
			publisher = Some("ICOS ERIC -- Carbon Portal"),
			publicationYear = Some(pubYear),
			types = Some(ResourceType(None, Some(ResourceTypeGeneral.Dataset))),
			subjects = dobj.keywords.getOrElse(Nil).map(keyword => Subject(keyword)),
			contributors = (creators.map(_.toContributor()) ++ plainContributors).distinct,
			dates = Seq(
				Some(doiDate(dobj.submission.start, DateType.Submitted)),
				tempCoverageDate(dobj),
				dobj.submission.stop.map(s => doiDate(s, DateType.Issued)),
				dobj.production.map(p => doiDate(p.dateTime, DateType.Created))
			).flatten,
			formats = Seq(),
			version = Some(Version(1, 0)),
			rightsList = Some(Seq(licence)),
			descriptions =
				dobj.specificInfo.left.toSeq.flatMap(_.description).map(d => Description(d, DescriptionType.Abstract, None)) ++
				dobj.specification.self.comments.map(comm => Description(comm, DescriptionType.Other, None)),
			geoLocations = dobj.coverage.map(DoiGeoLocationConverter.toDoiGeoLocation),
			fundingReferences = Option(
				CitationMaker.getFundingObjects(dobj).map(toFundingReference)
			).filterNot(_.isEmpty)
		)
	}

	def takeDate(ts: Instant): String = ts.toString.take(10)
	def doiDate(ts: Instant, dtype: DateType) = Date(takeDate(ts), Some(dtype))

	def tempCoverageDate(dobj: DataObject): Option[Date] =
		for
			acq <- dobj.acquisition;
			interval <- acq.interval
		yield
			val dateStr = takeDate(interval.start) + "/" + takeDate(interval.stop)
			Date(dateStr, Some(DateType.Collected))


	def makeDocObjectDoi(doc: DocObject)(implicit envri: Envri) = DoiMeta(
		doi = client.doi(CoolDoi.makeRandom),
		titles = doc.references.title.map(title => Seq(Title(title, None, None))),
		publisher = Some("ICOS ERIC -- Carbon Portal"),
		publicationYear = Some(Year.now.getValue),
		descriptions = doc.description.map(d => Description(d, DescriptionType.Abstract, None)).toSeq,
		creators = doc.references.authors.fold(Seq.empty[Creator])(_.map(toDoiCreator))
	)

	def makeCollectionDoi(coll: StaticCollection)(implicit envri: Envri): DoiMeta = {
		val creators = fetchCollObjectsRecursively(coll)
			.flatMap(_.references.authors.getOrElse(Nil))
			.distinct
			.map(toDoiCreator)

		DoiMeta(
			doi = client.doi(CoolDoi.makeRandom),
			creators = creators,
			titles = Some(Seq(Title(coll.title, None, None))),
			publisher = Some("ICOS ERIC -- Carbon Portal"),
			publicationYear = Some(Year.now.getValue),
			types = Some(ResourceType(None, Some(ResourceTypeGeneral.Collection))),
			subjects = Seq(),
			contributors = Seq(),
			dates = Seq(doiDate(Instant.now, DateType.Issued)),
			formats = Seq(),
			version = Some(Version(1, 0)),
			rightsList = Some(Seq(ccby4)),
			descriptions = coll.description.map(d => Description(d, DescriptionType.Abstract, None)).toSeq
		)
	}

	def toFundingReference(funding: Funding) = {
		val funderIdentifier = funding.funder.id.flatMap((s, idType) => {
			val scheme = idType match {
				case FunderIdType.`Crossref Funder ID` => FunderIdentifierScheme.Crossref
				case FunderIdType.GRID => FunderIdentifierScheme.Grid
				case FunderIdType.ISNI => FunderIdentifierScheme.Isni
				case FunderIdType.ROR => FunderIdentifierScheme.Ror
				case FunderIdType.Other => FunderIdentifierScheme.Other
			}

			Some(FunderIdentifier(Some(s), Some(scheme)))
		})

		FundingReference(
			Some(funding.funder.org.name), funderIdentifier,
			Some(Award(funding.awardTitle, funding.awardNumber, funding.awardUrl.flatMap(uri => Some(uri.toString))))
		)
	}

	def toDoiCreator(p: Agent) = p match {
		case Organization(_, name, _, _) =>
			Creator(
				name = GenericName(name),
				nameIdentifiers = Nil,
				affiliation = Nil
			)
		case Person(_, firstName, lastName, _, orcid) =>
			Creator(
				name = PersonalName(firstName, lastName),
				nameIdentifiers = orcid.map(orc => NameIdentifier(orc.shortId, NameIdentifierScheme.ORCID)).toSeq,
				affiliation = Nil
			)
	}

	def toDoiContributor(agent: Agent) = toDoiCreator(agent).toContributor()

	private def fetchCollObjectsRecursively(coll: StaticCollection): Seq[StaticObject] = coll.members.flatMap{
		case plain: PlainStaticObject => fetcher.fetchStaticObject(Uri(plain.res.toString))
		case coll: StaticCollection => fetchCollObjectsRecursively(coll)
	}

}

extension (creator: Creator)
	def toContributor(contrType: Option[ContributorType] = None) =
		Contributor(creator.name, creator.nameIdentifiers, creator.affiliation, contrType)