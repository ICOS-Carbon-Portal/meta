package se.lu.nateko.cp.meta.services.linkeddata

import java.net.{URI => JavaUri}

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshalling.{WithFixedContentType, WithOpenCharset}
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToResponseMarshaller}
import akka.http.scaladsl.model.Uri.Path.{Empty, Segment, Slash}
import akka.http.scaladsl.model.*
import akka.stream.Materializer
import org.eclipse.rdf4j.model.{IRI, Literal, Statement, ValueFactory}
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS}
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.Repository
import play.twirl.api.Html
import se.lu.nateko.cp.meta.{CpmetaConfig, api}
import se.lu.nateko.cp.meta.api.*
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.services.{CpVocab, MetadataException}
import se.lu.nateko.cp.meta.services.upload.{StaticObjectFetcher, DataObjectInstanceServers, PageContentMarshalling}
import se.lu.nateko.cp.meta.services.citation.PlainDoiCiter
import se.lu.nateko.cp.meta.utils.rdf4j.*
import spray.json.JsonWriter
import se.lu.nateko.cp.meta.views.ResourceViewInfo
import se.lu.nateko.cp.meta.views.ResourceViewInfo.PropValue
import scala.concurrent.{ExecutionContext, Future}
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import scala.util.Using
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.services.upload.CollectionReader
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.services.upload.DobjMetaReader
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection


trait UriSerializer {
	def marshaller: ToResponseMarshaller[Uri]
	def fetchStaticObject(uri: Uri): Validated[StaticObject]
	def fetchStaticCollection(uri: Uri): Validated[StaticCollection]
}

object UriSerializer{
	object Hash {
		def unapply(arg: String): Option[Sha256Sum] = Sha256Sum.fromString(arg).toOption

		object Object extends HashExtractor(objectPathPrefix.stripSuffix("/"))
		object Collection extends HashExtractor(collectionPathPrefix.stripSuffix("/"))

		abstract class HashExtractor(segment: String) {
			def unapply(arg: Uri.Path): Option[Sha256Sum] = arg match {
				case Slash(Segment(`segment`, Slash(Segment(Hash(hash), Empty)))) => Some(hash)
				case _ => None
			}
			def unapply(uri: JavaUri): Option[Sha256Sum] =
				val path = uri.getRawPath.stripPrefix("/")
				if !path.startsWith(segment) then None
				else Hash.unapply(path.stripPrefix(segment).stripPrefix("/"))
		}
	}

	object UriPath{
		def unapplySeq(path: Uri.Path): Seq[String] = path match {
			case Uri.Path.Slash(tail) => unapplySeq(tail)
			case Uri.Path.Segment(head, tail) => head +: unapplySeq(tail)
			case Uri.Path.Empty => Seq.empty
		}
	}
}

class Rdf4jUriSerializer(
	repo: Repository,
	servers: DataObjectInstanceServers,
	doiCiter: PlainDoiCiter,
	config: CpmetaConfig
)(using envries: EnvriConfigs, system: ActorSystem, mat: Materializer) extends UriSerializer:

	import TriplestoreConnection.{TSC2V, getLabeledResource, hasStatement}
	import InstanceServerSerializer.statementIterMarshaller
	import Rdf4jUriSerializer.*
	import UriSerializer.*
	import servers.{vocab, metaVocab, collectionLens}

	private given ValueFactory = repo.getValueFactory
	private val pidFactory = new api.HandleNetClient.PidFactory(config.dataUploadService.handle)
	private val citer = new CitationMaker(doiCiter, repo, config.core, system.log)
	private val collReader = CollectionReader(metaVocab, citer.getItemCitationInfo)
	private val objReader = StaticObjectReader(vocab, metaVocab, collReader, collectionLens, pidFactory, citer)
	private val pcm =
		val stats = new StatisticsClient(config.statsClient, config.core.envriConfigs)
		new PageContentMarshalling(config.core.handleProxies, stats)

	import pcm.{staticObjectMarshaller, statCollMarshaller}

	private val rdfMarshaller: ToResponseMarshaller[Uri] = statementIterMarshaller
		.compose(uri => () => getStatementsIter(uri, repo))

	val marshaller: ToResponseMarshaller[Uri] = Marshaller.oneOf(
		Marshaller[Uri, HttpResponse](
			implicit exeCtxt => uri => {
				given envri: Envri = inferEnvri(uri)
				given EnvriConfig = envries(envri)
				getMarshallings(uri)
			}
		),
		rdfMarshaller
	)
	private def inferEnvri(uri: Uri) = EnvriResolver.infer(new java.net.URI(uri.toString)).getOrElse(
		throw new MetadataException("Could not infer ENVRI from URL " + uri.toString)
	)

	def fetchStaticObject(uri: Uri): Validated[StaticObject] = uri.path match
		case Hash.Object(hash) =>
			given Envri = inferEnvri(uri)
			fetchStaticObj(hash)
		case _ => Validated.error(s"URI $uri does not have the shape of a data/document object URI")


	def fetchStaticCollection(uri: Uri): Validated[StaticCollection] = uri.path match
		case Hash.Collection(hash) =>
			given Envri = inferEnvri(uri)
			fetchStaticColl(hash)
		case _ => Validated.error(s"URI $uri does not have the shape of a collection URI")


	private def fetchStaticObj(hash: Sha256Sum)(using envri: Envri): Validated[StaticObject] =
		access(Validated.fromTry(servers.getInstServerForStaticObj(hash))):
			objReader.fetch(hash)


	private def fetchStaticColl(hash: Sha256Sum)(using Envri): Validated[StaticCollection] =
		access(servers.collectionServer):
			val collUri = vocab.getCollection(hash)
			collReader.fetchStatic(collUri, Some(hash))


	private def fetchStation(uri: Uri)(using Envri): VOE[Station] =
		access(servers.metaServer):
			objReader.getStation(uri.toRdf).map: st =>
				val membs = citer.attrProvider.getMemberships(st.org.self.uri)
				OrganizationExtra(st, membs)

	private def fetchOrg(uri: Uri)(using Envri): VOE[Organization] =
		access(servers.metaServer):
			objReader.getOrganization(uri.toRdf).map: org =>
				val membs = citer.attrProvider.getMemberships(org.self.uri)
				OrganizationExtra(org, membs)

	private def fetchPerson(uri: Uri)(using Envri): Validated[PersonExtra] =
		access(servers.metaServer):
			objReader.getPerson(uri.toRdf).map: pers =>
				val roles = citer.attrProvider.getPersonRoles(pers.self.uri)
				PersonExtra(pers, roles)

	private def fetchInstrument(uri: Uri)(using Envri): Validated[Instrument] =
		access(servers.metaServer):
			objReader.getInstrument(uri.toRdf)

	private def access[T](serverV: Validated[InstanceServer])(reader: TSC2V[T]): Validated[T] =
		serverV.flatMap(_.access(reader))

	private def readMetaRes[T](uri: Uri)(reader: (DobjMetaReader, IRI) => TSC2V[T])(using Envri): Validated[T] =
		servers.metaServer.flatMap(_.access(reader(objReader, uri.toRdf)))

	private def getDefaultHtml(uri: Uri)(charset: HttpCharset): HttpResponse = {
		implicit val envri = inferEnvri(uri)
		implicit val envriConfig = envries(envri)
		getViewInfo(uri, repo).fold(
			err => HttpResponse(
				status = StatusCodes.InternalServerError,
				entity = HttpEntity(
					ContentType.WithCharset(MediaTypes.`text/html`, charset),
					views.html.MessagePage("Server error", err.getMessage).body
				)
			),
			viewInfo => HttpResponse(
				status = if(viewInfo.isEmpty) StatusCodes.NotFound else StatusCodes.OK,
				entity = HttpEntity(
					ContentType.WithCharset(MediaTypes.`text/html`, charset),
					if(viewInfo.isEmpty) views.html.MessagePage("Page not found", "").body else views.html.UriResourcePage(viewInfo).body
				)
			)
		)
	}

	private def isObjSpec(uri: Uri)(using envri: Envri): Boolean =
		servers.metaServers(envri).access:
			hasStatement(Some(uri.toRdf), Some(servers.metaVocab.hasDataLevel), None)

	private def isLabeledRes(uri: Uri)(using envri: Envri): Boolean =
		servers.metaServers(envri).access:
			hasStatement(Some(uri.toRdf), Some(RDFS.LABEL), None)

	private def getMarshallings(uri: Uri)(using Envri, EnvriConfig, ExecutionContext): FLMHR = uri.path match
		case Hash.Object(hash) =>
			delegatedRepr(() => fetchStaticObj(hash))

		case Hash.Collection(hash) =>
			delegatedRepr(() => fetchStaticColl(hash))

		case UriPath("resources", "stations", stId) => oneOf(
			customHtml[OrganizationExtra[Station]](
				() => fetchStation(uri),
				(st, errors) => views.html.StationLandingPage(st, citer.vocab),
				views.html.MessagePage("Station not found", s"No station whose URL ends with $stId"),
				err => views.html.MessagePage("Station metadata error", s"Error fetching metadata for station $stId :\n${err.mkString("\n")}")
			),
			customJson(() => {
				fetchStation(uri.withQuery(Uri.Query.Empty))
			})
		)

		case UriPath("resources", "organizations", orgId) => oneOf(
			customHtml[OrganizationExtra[Organization]](
				() => fetchOrg(uri),
				(org, errors) => views.html.OrgLandingPage(org),
				views.html.MessagePage("Organization not found", s"No organization whose URL ends with $orgId"),
				err => views.html.MessagePage("Organization metadata error", s"Error fetching metadata for organization $orgId :\n${err.mkString("\n")}")
			),
			customJson(() => {
				fetchOrg(uri.withQuery(Uri.Query.Empty))
			})
		)

		case UriPath("resources", "instruments", instrId) => oneOf(
			customHtml[Instrument](
				() => readMetaRes(uri)(_ getInstrument _),
				(inst, errors) => views.html.InstrumentLandingPage(inst),
				views.html.MessagePage("Instrument not found", s"No instrument whose URL ends with $instrId"),
				err => views.html.MessagePage("Instrument metadata error", s"Error fetching metadata for instrument $instrId :\n${err.mkString("\n")}")
			),
			customJson(() => readMetaRes(uri)(_ getInstrument _))
		)

		case UriPath("resources", "people", persId) => oneOf(
			customHtml[PersonExtra](
				() => fetchPerson(uri),
				views.html.PersonLandingPage(_, _),
				views.html.MessagePage("Person not found", s"No person page whose URL ends with $persId"),
				errors => views.html.MessagePage(
					"Person metadata error",
					s"Error fetching metadata for person $persId :\n${errors.mkString("\n")}"
				)
			),
			customJson(() => fetchPerson(uri))(using OrganizationExtra.persExtraWriter)
		)

		case Slash(Segment("resources", _)) if isObjSpec(uri) => oneOf(
			customJson(() => readMetaRes(uri)(_ getSpecification _)),
			defaultHtml(uri)
		)

		case _ if isLabeledRes(uri) => oneOf(
			customJson(() => readMetaRes(uri)((_, uri) => getLabeledResource(uri))),
			defaultHtml(uri)
		)

		case _ =>
			oneOf(defaultHtml(uri))

	end getMarshallings

	private def oneOf(opts: Marshalling[HttpResponse]*): FLMHR  = Future.successful(opts.toList)

	private def delegatedRepr[T](fetchDto: () => Validated[T])(
		using trm: ToResponseMarshaller[() => Validated[T]], ctxt: ExecutionContext
	): FLMHR = trm(fetchDto)

	private def customJson[T : JsonWriter](fetchDto: () => Validated[T]): Marshalling[HttpResponse] =
		WithFixedContentType(ContentTypes.`application/json`, () => PageContentMarshalling.getJson(fetchDto()))

	import PageContentMarshalling.ErrorList

	private def customHtml[T](
		fetchDto: () => Validated[T],
		pageTemplate: (T, ErrorList) => Html,
		notFoundPage: => Html,
		errorPage: ErrorList => Html
	): Marshalling[HttpResponse] =
		PageContentMarshalling.twirlStatusHtmlMarshalling: () =>
			val itemV = fetchDto()
			itemV.result match
				case Some(value) => StatusCodes.OK -> pageTemplate(value, itemV.errors)
				case None =>
					if itemV.errors.isEmpty then StatusCodes.NotFound -> notFoundPage
					else StatusCodes.InternalServerError -> errorPage(itemV.errors)

	private def defaultHtml(uri: Uri): Marshalling[HttpResponse] =
		WithOpenCharset(MediaTypes.`text/html`, getDefaultHtml(uri))

end Rdf4jUriSerializer

private object Rdf4jUriSerializer{

	type FLMHR = Future[List[Marshalling[HttpResponse]]]
	type VOE[O] = Validated[OrganizationExtra[O]]

	val Limit = 500

	private def getStatementsIter(res: Uri, repo: Repository): CloseableIterator[Statement] = {
		val uri = repo.getValueFactory.createIRI(res.toString)
		repo.access(conn => conn.getStatements(uri, null, null, false)) ++
		repo.access(conn => conn.getStatements(null, null, uri, false))
	}

	def getViewInfo(res: Uri, repo: Repository): Try[ResourceViewInfo] = Using.Manager{use =>
		val conn = use(repo.getConnection())

		val propInfos = use(
			conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceViewInfoQuery(res)).evaluate().asCloseableIterator
		).map{bset =>

			val propUriOpt: Option[UriResource] = getOptUriRes(bset, "prop", "propLabel")

			val propValueOpt: Option[PropValue] = bset.getValue("val") match {
				case uri: IRI =>
					val valLabel = getOptLit(bset, "valLabel")
					Some(Left(UriResource(uri.toJava, valLabel, Nil)))
				case lit: Literal =>
					Some(Right(lit.stringValue))
				case _ => None
			}
			propUriOpt zip propValueOpt
		}.flatten.take(Limit).toIndexedSeq

		val usageInfos = use(
			conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceUsageInfoQuery(res)).evaluate().asCloseableIterator
		).map{bset =>
			getOptUriRes(bset, "obj", "objLabel") zip getOptUriRes(bset, "prop", "propLabel")
		}.flatten.take(Limit).toIndexedSeq

		val uri = JavaUri.create(res.toString)
		val seed = ResourceViewInfo(UriResource(uri, None, Nil), Nil, Nil, usageInfos)

		propInfos.foldLeft(seed)((acc, propAndVal) => propAndVal match {

			case (UriResource(propUri, _, _), Right(strVal)) if(propUri === RDFS.LABEL) =>
				acc.copy(res = acc.res.copy(label = Some(strVal)))

			case (UriResource(propUri, _, _), Right(strVal)) if(propUri === RDFS.COMMENT) =>
				acc.copy(res = acc.res.copy(comments = acc.res.comments :+ strVal))

			case (UriResource(propUri, _, _), Left(rdfType)) if(propUri === RDF.TYPE) =>
				acc.copy(types = rdfType :: acc.types)

			case _ =>
				acc.copy(propValues = propAndVal :: acc.propValues)
		})
	}


	private def getOptUriRes(bset: BindingSet, varName: String, lblName: String): Option[UriResource] = {
		bset.getValue(varName) match {
			case uri: IRI =>
				val label = getOptLit(bset, lblName)
				Some(UriResource(uri.toJava, label, Nil))
			case _ => None
		}
	}

	private def getOptLit(bset: BindingSet, varName: String): Option[String] = {
		bset.getValue(varName) match {
			case null => None
			case lit: Literal => Some(lit.stringValue)
			case _ => None
		}
	}

	def resourceViewInfoQuery(res: Uri) =
		s"""SELECT ?prop ?propLabel ?val ?valLabel
		|WHERE{
		|	<${res.toString}> ?prop ?val .
		|	OPTIONAL {?prop rdfs:label ?propLabel}
		|	OPTIONAL {?val rdfs:label ?valLabel}
		|}""".stripMargin

	def resourceUsageInfoQuery(res: Uri) =
		s"""SELECT ?obj ?objLabel ?prop ?propLabel
		|WHERE{
		|	?obj ?prop <${res.toString}> .
		|	OPTIONAL {?obj rdfs:label ?objLabel}
		|	OPTIONAL {?prop rdfs:label ?propLabel}
		|}""".stripMargin

}
