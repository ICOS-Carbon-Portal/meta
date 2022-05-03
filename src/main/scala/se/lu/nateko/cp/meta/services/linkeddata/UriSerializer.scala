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
import se.lu.nateko.cp.meta.services.citation.CitationClient
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

trait UriSerializer {
	def marshaller: ToResponseMarshaller[Uri]
	def fetchStaticObject(uri: Uri): Option[StaticObject]
	def fetchStaticCollection(uri: Uri): Option[StaticCollection]
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
		}
	}

}

class Rdf4jUriSerializer(
	repo: Repository,
	servers: DataObjectInstanceServers,
	doiCiter: CitationClient,
	config: CpmetaConfig
)(implicit envries: EnvriConfigs, system: ActorSystem, mat: Materializer) extends UriSerializer{

	import InstanceServerSerializer.statementIterMarshaller
	import Rdf4jUriSerializer.*
	import UriSerializer.Hash

	private given ValueFactory = repo.getValueFactory
	private val pidFactory = new api.HandleNetClient.PidFactory(config.dataUploadService.handle)
	private val citer = new CitationMaker(doiCiter, repo, config.core)
	val stats = new StatisticsClient(config.statsClient, config.core.envriConfigs)
	val pcm = new PageContentMarshalling(config.core.handleProxies, stats)

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
	private def inferEnvri(uri: Uri) = Envri.infer(new java.net.URI(uri.toString)).getOrElse(
		throw new MetadataException("Could not infer ENVRI from URL " + uri.toString)
	)

	def fetchStaticObject(uri: Uri): Option[StaticObject] = uri.path match {
		case Hash.Object(hash) => fetchStaticObj(hash)(inferEnvri(uri))
		case _ => None
	}

	def fetchStaticCollection(uri: Uri): Option[StaticCollection] = uri.path match {
		case Hash.Collection(hash) => fetchStaticColl(hash)(inferEnvri(uri))
		case _ => None
	}

	private def fetchStaticObj(hash: Sha256Sum)(implicit envri: Envri): Option[StaticObject] = {
		import servers.vocab
		for(
			server <- servers.getInstServerForStaticObj(hash).toOption;
			collFetcher <- servers.collFetcherLite;
			metaFetcher <- servers.metaFetchers.get(envri);
			plainFetcher = metaFetcher.plainObjFetcher;
			objectFetcher = new StaticObjectFetcher(server, collFetcher, plainFetcher, pidFactory, citer);
			dobj <- objectFetcher.fetch(hash)
		) yield dobj
	}

	private def fetchStaticColl(hash: Sha256Sum)(implicit envri: Envri): Option[StaticCollection] =
		servers.collFetcher(citer).flatMap(_.fetchStatic(hash))

	private def fetchStation(iri: IRI)(implicit  envri: Envri): Try[Option[StationExtra]] = servers.getStation(iri).map{stOpt =>
		stOpt.map{st =>
			val membs = citer.attrProvider.getMemberships(st).toIndexedSeq
			new StationExtra(st, membs)
		}
	}

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

	private def makeIri(uri: Uri) = JavaUri.create(uri.toString).toRdf

	private def isObjSpec(uri: Uri)(implicit envri: Envri): Boolean =
		servers.metaServers(envri).hasStatement(Some(makeIri(uri)), Some(servers.metaVocab.hasDataLevel), None)

	private def isLabeledRes(uri: Uri)(implicit envri: Envri): Boolean =
		servers.metaServers(envri).hasStatement(Some(makeIri(uri)), Some(RDFS.LABEL), None)

	private def getMarshallings(uri: Uri)(using Envri, EnvriConfig, ExecutionContext): FLMHR = uri.path match {

		case Hash.Object(hash) =>
			delegatedRepr(() => fetchStaticObj(hash))

		case Hash.Collection(hash) =>
			delegatedRepr(() => fetchStaticColl(hash))

		case Slash(Segment("resources", Slash(Segment("stations", stId)))) => oneOf(
			customHtml[StationExtra](
				() => fetchStation(makeIri(uri)),
				st => views.html.StationLandingPage(st, citer.vocab),
				views.html.MessagePage("Station not found", s"No station whose URL ends with $stId"),
				err => views.html.MessagePage("Station metadata error", s"Error fetching metadata for station $stId :\n${err.getMessage}")
			),
			customJson(() => {
				fetchStation(makeIri(uri.withQuery(Uri.Query.Empty)))
			})
		)

		case Slash(Segment("resources", Slash(Segment("instruments", instrId)))) => oneOf(
			customHtml[Instrument](
				() => servers.metaFetcher.map(_.getInstrument(makeIri(uri))),
				inst => views.html.InstrumentLandingPage(inst),
				views.html.MessagePage("Instrument not found", s"No instrument whose URL ends with $instrId"),
				err => views.html.MessagePage("Instrument metadata error", s"Error fetching metadata for instrument $instrId :\n${err.getMessage}")
			),
			customJson(() =>
				servers.metaFetcher.map(_.getInstrument(makeIri(uri)))
			)
		)

		case Slash(Segment("resources", _)) if isObjSpec(uri) => oneOf(
			customJson(() => servers.getDataObjSpecification(makeIri(uri)).map(Some(_))),
			defaultHtml(uri)
		)

		case _ if isLabeledRes(uri) => oneOf(
			customJson(() => servers.metaFetcher.map(_.getLabeledResource(makeIri(uri))).map(Some(_))),
			defaultHtml(uri)
		)

		case _ =>
			oneOf(defaultHtml(uri))
	}

	private def oneOf(opts: Marshalling[HttpResponse]*): FLMHR  = Future.successful(opts.toList)

	private def delegatedRepr[T](fetchDto: () => Option[T])(
		implicit trm: ToResponseMarshaller[() => Option[T]], ctxt: ExecutionContext
	): FLMHR = trm(fetchDto)

	private def customJson[T : JsonWriter](fetchDto: () => Try[Option[T]]): Marshalling[HttpResponse] =
		WithFixedContentType(ContentTypes.`application/json`, () => PageContentMarshalling.getJson(fetchDto()))

	private def customHtml[T](
		fetchDto: () => Try[Option[T]],
		pageTemplate: T => Html,
		notFoundPage: => Html,
		errorPage: Throwable => Html
	): Marshalling[HttpResponse] =
		PageContentMarshalling.twirlStatusHtmlMarshalling{
			() => fetchDto() match {
				case Success(Some(value)) => StatusCodes.OK -> pageTemplate(value)
				case Success(None) => StatusCodes.NotFound -> notFoundPage
				case Failure(err) => StatusCodes.InternalServerError -> errorPage(err)
			}
		}

	private def defaultHtml(uri: Uri): Marshalling[HttpResponse] =
		WithOpenCharset(MediaTypes.`text/html`, getDefaultHtml(uri))
}

private object Rdf4jUriSerializer{

	type FLMHR = Future[List[Marshalling[HttpResponse]]]

	val Limit = 500

	private def getStatementsIter(res: Uri, repo: Repository): CloseableIterator[Statement] = {
		val uri = repo.getValueFactory.createIRI(res.toString)
		repo.access(conn => conn.getStatements(uri, null, null, false)) ++
		repo.access(conn => conn.getStatements(null, null, uri, false))
	}

	def getViewInfo(res: Uri, repo: Repository): Try[ResourceViewInfo] = Using.Manager{use =>
		val conn = use(repo.getConnection())

		val propInfos = use(
			new Rdf4jIterationIterator(
				conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceViewInfoQuery(res)).evaluate()
			)
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
			new Rdf4jIterationIterator(
				conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceUsageInfoQuery(res)).evaluate()
			)
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
