package se.lu.nateko.cp.meta.services.linkeddata

import java.net.{URI => JavaUri}

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshalling.{WithFixedContentType, WithOpenCharset}
import akka.http.scaladsl.marshalling.{Marshaller, Marshalling, ToResponseMarshaller}
import akka.http.scaladsl.model.Uri.Path.{Empty, Segment, Slash}
import akka.http.scaladsl.model._
import akka.stream.Materializer
import org.eclipse.rdf4j.model.{IRI, Literal, Statement}
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS}
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.Repository
import play.twirl.api.Html
import se.lu.nateko.cp.meta.{CpmetaConfig, api}
import se.lu.nateko.cp.meta.api._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.Envri.{Envri, EnvriConfigs}
import se.lu.nateko.cp.meta.core.data.JsonSupport.stationFormat
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.services.{CpVocab, MetadataException}
import se.lu.nateko.cp.meta.services.upload.{StaticObjectFetcher, DataObjectInstanceServers, PageContentMarshalling}
import se.lu.nateko.cp.meta.utils.rdf4j._
import spray.json.JsonWriter
import se.lu.nateko.cp.meta.views.ResourceViewInfo
import se.lu.nateko.cp.meta.views.ResourceViewInfo.PropValue

import scala.concurrent.{ExecutionContext, Future}

trait UriSerializer {
	def marshaller: ToResponseMarshaller[Uri]
}

class Rdf4jUriSerializer(
	repo: Repository,
	servers: DataObjectInstanceServers,
	config: CpmetaConfig
)(implicit envries: EnvriConfigs, system: ActorSystem, mat: Materializer) extends UriSerializer{

	import InstanceServerSerializer.statementIterMarshaller
	import Rdf4jUriSerializer._

	private val pidFactory = new api.HandleNetClient.PidFactory(config.dataUploadService.handle)
	val citer = new CitationClient(getDois, config.citations)
	val stats = new StatisticsClient(config.restheart)
	val pcm = new PageContentMarshalling(config.core.handleService, citer, new CpVocab(repo.getValueFactory), stats)

	import pcm.{dataObjectMarshaller, statCollMarshaller}

	private val rdfMarshaller: ToResponseMarshaller[Uri] = statementIterMarshaller
		.compose(uri => () => getStatementsIter(uri, repo))

	val marshaller: ToResponseMarshaller[Uri] = Marshaller.oneOf(
			Marshaller(
			implicit exeCtxt => uri => {
				implicit val envri = inferEnvri(uri)
				implicit val envriConfig = envries(envri)
				getReprOptions(uri).marshal
			}
		),
		rdfMarshaller
	)
	private def inferEnvri(uri: Uri) = Envri.infer(new java.net.URI(uri.toString)).getOrElse(
		throw new MetadataException("Could not infer ENVRI from URL " + uri.toString)
	)

	private def fetchStaticObj(hash: Sha256Sum)(implicit envri: Envri): Option[StaticObject] = {
		import servers.vocab
		for(
			server <- servers.getInstServerForStaticObj(hash).toOption;
			collFetcher <- servers.collFetcher;
			objectFetcher = new StaticObjectFetcher(server, vocab, collFetcher, pidFactory);
			dobj <- objectFetcher.fetch(hash)
		) yield dobj
	}

	private def fetchStaticColl(hash: Sha256Sum)(implicit envri: Envri): Option[StaticCollection] =
		servers.collFetcher.flatMap(_.fetchStatic(hash))

	private def fetchStation(iri: IRI)(implicit  envri: Envri): Option[Station] = servers.getStation(iri)

	private def getDefaultHtml(uri: Uri)(charset: HttpCharset) = {
		implicit val envri = inferEnvri(uri)
		val viewInfo = getViewInfo(uri, repo, envri)
		HttpResponse(
			status = if(viewInfo.isEmpty) StatusCodes.NotFound else StatusCodes.OK,
			entity = HttpEntity(
				ContentType.WithCharset(MediaTypes.`text/html`, charset),
				if(viewInfo.isEmpty) views.html.MessagePage("Page not found", "").body else views.html.UriResourcePage(viewInfo).body
			)
		)
	}

	private def getReprOptions(uri: Uri)(implicit envri: Envri, envriConfig: EnvriConfig): RepresentationOptions = uri.path match {
		case Hash.Object(hash) =>
			new DelegatingRepr(() => fetchStaticObj(hash))
		case Hash.Collection(hash) =>
			new DelegatingRepr(() => fetchStaticColl(hash))
		case Slash(Segment("resources", Slash(Segment("stations", stId)))) =>
			new FullCustomization[Station](
				() => fetchStation(JavaUri.create(uri.toString).toRdf(repo.getValueFactory)),
				views.html.StationLandingPage(_),
				views.html.MessagePage("Station not found", s"No station whose URL ends with $stId")
			)
		case _ =>
			new DefaultReprOptions(uri)
	}

	private sealed trait RepresentationOptions{
		def marshal(implicit ctxt: ExecutionContext): Future[List[Marshalling[HttpResponse]]]
	}

	private class DelegatingRepr[T](fetchDto: () => Option[T])(implicit trm: ToResponseMarshaller[() => Option[T]]) extends RepresentationOptions {
		override def marshal(implicit ctxt: ExecutionContext) = trm(fetchDto)
	}

	private class WithJson[T : JsonWriter](fetchDto: () => Option[T]) extends RepresentationOptions {
		override def marshal(implicit ctxt: ExecutionContext): Future[List[Marshalling[HttpResponse]]] = Future.successful{
			WithFixedContentType(ContentTypes.`application/json`, () => PageContentMarshalling.getJson(fetchDto())) :: Nil
		}
	}

	private class FullCustomization[T : JsonWriter](
			fetchDto: () => Option[T],
			pageTemplate: T => Html,
			notFoundPage: => Html
	) extends WithJson[T](fetchDto) {
		override def marshal(implicit ctxt: ExecutionContext): Future[List[Marshalling[HttpResponse]]] =
			super.marshal.map{json =>
				PageContentMarshalling.twirlStatusHtmlMarshalling{
					() => fetchDto() match {
						case Some(value) => StatusCodes.OK -> pageTemplate(value)
						case None => StatusCodes.NotFound -> notFoundPage
					}
				} :: json
			}
	}
	private class DefaultReprOptions(uri: Uri) extends RepresentationOptions {
		override def marshal(implicit ctxt: ExecutionContext) = Future.successful{
			WithOpenCharset(MediaTypes.`text/html`, getDefaultHtml(uri)) :: Nil
		}
	}

	private def getDois: List[Doi] = {
		import se.lu.nateko.cp.meta.services.CpmetaVocab
		import se.lu.nateko.cp.meta.utils.rdf4j._
		val meta = new CpmetaVocab(repo.getValueFactory)
		repo
			.access{conn =>
				conn.getStatements(null, meta.hasDoi, null)
			}
			.map(_.getObject.stringValue)
			.toList.distinct.collect{
			case Doi(doi) => doi
		}
	}
}



private object Rdf4jUriSerializer{

	object Hash {
		def unapply(arg: String): Option[Sha256Sum] = Sha256Sum.fromString(arg).toOption

		object Object extends HashExtractor("objects")
		object Collection extends HashExtractor("collections")

		abstract class HashExtractor(segment: String) {
			def unapply(arg: Uri.Path): Option[Sha256Sum] = arg match {
				case Slash(Segment(`segment`, Slash(Segment(Hash(hash), Empty)))) => Some(hash)
				case _ => None
			}
		}
	}
	val Limit = 500

	def getStatementsIter(res: Uri, repo: Repository): CloseableIterator[Statement] = {
		val uri = repo.getValueFactory.createIRI(res.toString)
		val own = repo.access(conn => conn.getStatements(uri, null, null, false))
		val about = repo.access(conn => conn.getStatements(null, null, uri, false))
		own ++ about
	}

	def getViewInfo(res: Uri, repo: Repository, envri: Envri.Value): ResourceViewInfo = repo.accessEagerly{conn =>

		val propInfo = conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceViewInfoQuery(res)).evaluate()

		val propInfos = new Rdf4jIterationIterator(propInfo).map{bset =>

			val propUriOpt: Option[UriResource] = getOptUriRes(bset, "prop", "propLabel")

			val propValueOpt: Option[PropValue] = bset.getValue("val") match {
				case uri: IRI =>
					val valLabel = getOptLit(bset, "valLabel")
					Some(Left(UriResource(uri.toJava, valLabel)))
				case lit: Literal =>
					Some(Right(lit.stringValue))
				case _ => None
			}
			propUriOpt zip propValueOpt
		}.flatten.take(Limit).toIndexedSeq

		val usageInfo = conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceUsageInfoQuery(res)).evaluate()

		val usageInfos = new Rdf4jIterationIterator(usageInfo).map{bset =>
			getOptUriRes(bset, "obj", "objLabel") zip getOptUriRes(bset, "prop", "propLabel")
		}.flatten.take(Limit).toIndexedSeq

		val uri = JavaUri.create(res.toString)
		val seed = ResourceViewInfo(UriResource(uri, None), envri, None, Nil, Nil, usageInfos)

		propInfos.foldLeft(seed)((acc, propAndVal) => propAndVal match {

			case (UriResource(propUri, _), Right(strVal)) if(propUri === RDFS.LABEL) =>
				acc.copy(res = UriResource(uri, Some(strVal)))

			case (UriResource(propUri, _), Right(strVal)) if(propUri === RDFS.COMMENT) =>
				acc.copy(comment = Some(strVal))

			case (UriResource(propUri, _), Left(rdfType)) if(propUri === RDF.TYPE) =>
				acc.copy(types = rdfType :: acc.types)

			case _ =>
				acc.copy(propValues = propAndVal :: acc.propValues)
		})
	}

	private def getOptUriRes(bset: BindingSet, varName: String, lblName: String): Option[UriResource] = {
		bset.getValue(varName) match {
			case uri: IRI =>
				val label = getOptLit(bset, lblName)
				Some(UriResource(uri.toJava, label))
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
