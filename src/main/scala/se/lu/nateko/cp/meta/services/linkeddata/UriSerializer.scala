package se.lu.nateko.cp.meta.services.linkeddata

import java.net.{URI => JavaUri}

import scala.collection.TraversableOnce.flattenTraversableOnce
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository
import akka.http.scaladsl.marshalling.{Marshal, Marshaller, Marshalling, ToResponseMarshaller}
import akka.http.scaladsl.marshalling.Marshalling.WithFixedContentType
import akka.http.scaladsl.marshalling.Marshalling.WithOpenCharset
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.model.HttpCharset
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.Uri
import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri.Path.{Empty, Segment, Slash}
import play.twirl.api.Html
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.api.{CloseableIterator, EpicPidClient}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data._
import se.lu.nateko.cp.meta.core.data.JsonSupport.stationFormat
import se.lu.nateko.cp.meta.core.data.Envri.{Envri, EnvriConfigs}
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.services.upload.{DataObjectFetcher, DataObjectInstanceServers, PageContentMarshalling}
import se.lu.nateko.cp.meta.utils.rdf4j._
import spray.json.JsonWriter
import views.html.ResourceViewInfo
import views.html.ResourceViewInfo.PropValue

trait UriSerializer {
	def marshaller: ToResponseMarshaller[Uri]
}

class Rdf4jUriSerializer(
	repo: Repository,
	servers: DataObjectInstanceServers,
	config: CpmetaConfig,
	pcm: PageContentMarshalling
)(implicit envries: EnvriConfigs, system: ActorSystem) extends UriSerializer{

	import InstanceServerSerializer.statementIterMarshaller
	import Rdf4jUriSerializer._
	import pcm.{dataObjectMarshaller, statCollMarshaller}

	private val rdfMarshaller: ToResponseMarshaller[Uri] = statementIterMarshaller
		.compose(uri => () => getStatementsIter(uri, repo))

	val marshaller: ToResponseMarshaller[Uri] = Marshaller(
		implicit exeCtxt => uri => {
			implicit val envri = inferEnvri(uri)
			implicit val envriConfig = envries(envri)
			getReprOptions(uri).marshal
		}
	)
	private def inferEnvri(uri: Uri) = Envri.infer(new java.net.URI(uri.toString)).getOrElse(
		throw new MetadataException("Could not infer ENVRI from URL " + uri.toString)
	)

	private def fetchDataObj(hash: Sha256Sum)(implicit envri: Envri): Option[DataObject] = {
		import servers.vocab
		val epic = new EpicPidClient(config.dataUploadService.epicPid)
		val server = servers.getInstServerForDataObj(hash).get
		val collFetcher = servers.collFetcher.get
		val objectFetcher = new DataObjectFetcher(server, vocab, collFetcher, epic.getPid)
		objectFetcher.fetch(hash)
	}

	private def fetchStaticColl(hash: Sha256Sum)(implicit envri: Envri): Option[StaticCollection] =
		servers.collFetcher.flatMap(_.fetchStatic(hash))

	private def fetchStation(name: IRI)(implicit  envri: Envri): Option[Station] = Some(servers.getStation(name))

	private def getDefaultHtml(uri: Uri)(charset: HttpCharset) = {
		HttpResponse(
			entity = HttpEntity(
				ContentType.WithCharset(MediaTypes.`text/html`, charset),
				views.html.UriResourcePage(getViewInfo(uri, repo, inferEnvri(uri))).body
			)
		)
	}

	private def getReprOptions(uri: Uri)(implicit envri: Envri, envriConfig: EnvriConfig): RepresentationOptions = uri.path match {
		case Hash.Object(hash) =>
			new DelegatingRepr(() => fetchDataObj(hash))
		case Hash.Collection(hash) =>
			new DelegatingRepr(() => fetchStaticColl(hash))
		case Slash(Segment("resources", Slash(Segment("stations", _)))) =>
			new FullCustomization[Station](
				() => fetchStation(JavaUri.create(uri.toString()).toRdf(repo.getValueFactory)),
				station => views.html.StationLandingPage(station)
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
	private class FullCustomization[T : JsonWriter](fetchDto: () => Option[T], pageTemplate: Option[T] => Html) extends WithJson[T](fetchDto) {
		override def marshal(implicit ctxt: ExecutionContext): Future[List[Marshalling[HttpResponse]]] = {
			super.marshal.zip(PageContentMarshalling.twirlHtmlMarshaller(pageTemplate(fetchDto()))).map{
				case (json, html) => json ++ html
			}
		}
	}
	private class DefaultReprOptions(uri: Uri) extends RepresentationOptions {
		override def marshal(implicit ctxt: ExecutionContext): Future[List[Marshalling[HttpResponse]]] = rdfMarshaller(uri).map{
			WithOpenCharset(MediaTypes.`text/html`, getDefaultHtml(uri)) :: _
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
