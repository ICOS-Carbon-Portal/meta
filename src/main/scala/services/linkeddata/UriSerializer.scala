package se.lu.nateko.cp.meta.services.linkeddata

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.Marshalling
import akka.http.scaladsl.marshalling.Marshalling.WithFixedContentType
import akka.http.scaladsl.marshalling.Marshalling.WithOpenCharset
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.Uri.Path.Empty
import akka.http.scaladsl.model.Uri.Path.Segment
import akka.http.scaladsl.model.Uri.Path.Slash
import akka.http.scaladsl.model.*
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.Statement
import play.twirl.api.Html
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.api
import se.lu.nateko.cp.meta.api.*
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.services.citation.AttributionProvider
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataClient
import se.lu.nateko.cp.meta.services.upload.PageContentMarshalling
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.*
import spray.json.JsonWriter

import java.net.{URI => JavaUri}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import se.lu.nateko.cp.meta.services.CpmetaVocab


trait UriSerializer {
	def marshaller: ToResponseMarshaller[Uri]
	def fetchStaticObject(uri: Uri): Validated[StaticObject]
	def fetchStaticCollection(uri: Uri): Validated[StaticCollection]
	def fetchStaticObjectWithDerived(uri: Uri): Future[Validated[StaticObject]]
	def fetchStaticCollectionWithDerived(uri: Uri): Future[Validated[StaticCollection]]
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
	vocab: CpVocab,
	metaVocab: CpmetaVocab,
	lenses: RdfLenses,
	derivedMetadata: DerivedMetadataClient,
	config: CpmetaConfig
)(using envries: EnvriConfigs, system: ActorSystem, mat: Materializer) extends UriSerializer:

	import InstanceServerSerializer.statementIterMarshaller
	import Rdf4jUriSerializer.*
	import UriSerializer.*
	private given ExecutionContext = system.dispatcher

	private val pidFactory = new api.HandleNetClient.PidFactory(config.dataUploadService.handle)
	private val attribution = new AttributionProvider(vocab, metaVocab)
	private val landingPageLoader = new LandingPageLoader(repo, vocab, metaVocab, lenses, pidFactory, attribution)
	private val pageContentMarshalling =
		val stats = new StatisticsClient(config.statsClient, config.core.envriConfigs)
		new PageContentMarshalling(config.core.handleProxies, stats)

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
	private def inferEnvri(uri: Uri) = EnvriResolver.infer(new JavaUri(uri.toString)).getOrElse(
		throw new MetadataException("Could not infer ENVRI from URL " + uri.toString)
	)

	def fetchStaticObject(uri: Uri): Validated[StaticObject] = uri.path match
		case Hash.Object(hash) =>
			given Envri = inferEnvri(uri)
			landingPageLoader.staticObject(hash)
		case _ => Validated.error(s"URI $uri does not have the shape of a data/document object URI")
	def fetchStaticCollection(uri: Uri): Validated[StaticCollection] = uri.path match
		case Hash.Collection(hash) =>
			given Envri = inferEnvri(uri)
			landingPageLoader.staticCollection(hash)
		case _ => Validated.error(s"URI $uri does not have the shape of a collection URI")

	private def enrich[T](parsed: Validated[T])(fetch: T => Future[T]): Future[Validated[T]] =
		parsed.result.fold(Future.successful(new Validated[T](None, parsed.errors))): item =>
			fetch(item)
				.map(enriched => new Validated(Some(enriched), parsed.errors))
				.recover { case err =>
					parsed.withExtraError(s"Could not fetch derived metadata from rdfStore: ${err.getMessage}")
				}

	def fetchStaticObjectWithDerived(uri: Uri): Future[Validated[StaticObject]] =
		enrich(fetchStaticObject(uri))(derivedMetadata.enrich(new JavaUri(uri.toString), _))

	def fetchStaticCollectionWithDerived(uri: Uri): Future[Validated[StaticCollection]] =
		enrich(fetchStaticCollection(uri))(derivedMetadata.enrich(new JavaUri(uri.toString), _))

	private def getDefaultHtml(uri: Uri)(charset: HttpCharset): HttpResponse =
		given envri: Envri = inferEnvri(uri)
		given EnvriConfig = envries(envri)
		landingPageLoader.genericResource(uri).fold(
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
					if(viewInfo.isEmpty) views.html.MessagePage("Page not found", "The requested page could not be found.").body else views.html.UriResourcePage(viewInfo).body
				)
			)
		)


	private def getMarshallings(uri: Uri)(using Envri, EnvriConfig, ExecutionContext): FLMHR =

		def resourceMarshallings[T : JsonWriter](
			resId: String, resourceType: String, fetcher: Uri => Validated[T],
			pageTemplate: (T, PageContentMarshalling.ErrorList) => Html
		): FLMHR =
			lazy val itemV = fetcher(uri.withQuery(Uri.Query.Empty))
			oneOf(
				PageContentMarshalling.twirlStatusHtmlMarshalling: () =>
					itemV.result match
						case Some(value) =>
							StatusCodes.OK -> pageTemplate(value, itemV.errors)
						case None =>
							if itemV.errors.isEmpty then
								val notFoundPage = views.html.MessagePage(
									s"${resourceType.capitalize} not found",
									s"No $resourceType page whose URL ends with $resId"
								)
								StatusCodes.NotFound -> notFoundPage
							else
								val errorPage = views.html.MessagePage(
									s"${resourceType.capitalize} metadata error",
									s"Error fetching metadata for $resourceType $resId :\n${itemV.errors.mkString("\n")}"
								)
								StatusCodes.InternalServerError -> errorPage
				,
				customJson(() => itemV)
			)

		uri.path match
			case Hash.Object(hash) =>
				given CpVocab = vocab
				pageContentMarshalling.staticObjectAsyncMarshaller(() => fetchStaticObjectWithDerived(uri))

			case Hash.Collection(hash) =>
				pageContentMarshalling.staticCollectionAsyncMarshaller(() => fetchStaticCollectionWithDerived(uri))

			case UriPath("resources", "stations", stId) => resourceMarshallings(
				stId, "station", landingPageLoader.station,
				(st, errors) => views.html.StationLandingPage(st, vocab, errors)
			)

			case UriPath("resources", "organizations", orgId) => resourceMarshallings(
				orgId, "organization", landingPageLoader.organization,
				views.html.OrgLandingPage(_, _)
			)

			case UriPath("resources", "instruments", instrId) => resourceMarshallings(
				instrId, "instrument", landingPageLoader.instrument,
				views.html.InstrumentLandingPage(_, _)
			)

			case UriPath("resources", "people", persId) => resourceMarshallings(
				persId, "person", landingPageLoader.person,
				views.html.PersonLandingPage(_, _)
			)(using OrganizationExtra.persExtraWriter)

			case Slash(Segment("resources", _)) if landingPageLoader.isObjectSpecification(uri) => oneOf(
				customJson(() => landingPageLoader.specification(uri)),
				defaultHtml(uri)
			)

			case _ if landingPageLoader.isLabeledResource(uri) => oneOf(
				customJson(() => landingPageLoader.labeledResource(uri)),
				defaultHtml(uri)
			)

			case _ =>
				oneOf(defaultHtml(uri))

	end getMarshallings

	private def oneOf(opts: Marshalling[HttpResponse]*): FLMHR  = Future.successful(opts.toList)

	private def customJson[T : JsonWriter](fetchDto: () => Validated[T]): Marshalling[HttpResponse] =
		WithFixedContentType(ContentTypes.`application/json`, () => PageContentMarshalling.getJson(fetchDto()))

	private def defaultHtml(uri: Uri): Marshalling[HttpResponse] =
		WithOpenCharset(MediaTypes.`text/html`, getDefaultHtml(uri))

end Rdf4jUriSerializer


private object Rdf4jUriSerializer{

	type FLMHR = Future[List[Marshalling[HttpResponse]]]

	private def getStatementsIter(res: Uri, repo: Repository): CloseableIterator[Statement] = {
		val uri = repo.getValueFactory.createIRI(res.toString)
		repo.access(conn => conn.getStatements(uri, null, null, false)) ++
		repo.access(conn => conn.getStatements(null, null, uri, false))
	}

}
