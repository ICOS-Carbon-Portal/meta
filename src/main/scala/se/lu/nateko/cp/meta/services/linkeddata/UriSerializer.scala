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
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository
import play.twirl.api.Html
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.api
import se.lu.nateko.cp.meta.api.*
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.{TriplestoreConnection, StatementSource}
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.MetadataException
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import se.lu.nateko.cp.meta.services.citation.PlainDoiCiter
import se.lu.nateko.cp.meta.services.upload.PageContentMarshalling
import se.lu.nateko.cp.meta.services.upload.PageContentMarshalling.ErrorList
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.views.ResourceViewInfo
import se.lu.nateko.cp.meta.views.ResourceViewInfo.PropValue
import spray.json.JsonWriter

import java.net.{URI => JavaUri}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scala.util.Using
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer


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
	vocab: CpVocab,
	metaVocab: CpmetaVocab,
	lenses: RdfLenses,
	doiCiter: PlainDoiCiter,
	config: CpmetaConfig
)(using envries: EnvriConfigs, system: ActorSystem, mat: Materializer) extends UriSerializer:

	import se.lu.nateko.cp.meta.instanceserver.StatementSource.{getLabeledResource, hasStatement}
	import InstanceServerSerializer.statementIterMarshaller
	import Rdf4jUriSerializer.*
	import UriSerializer.*
	import RdfLens.{MetaConn, GlobConn, DocConn}

	private given ValueFactory = repo.getValueFactory
	private val server = new Rdf4jInstanceServer(repo)
	private val pidFactory = new api.HandleNetClient.PidFactory(config.dataUploadService.handle)
	private val citer = new CitationMaker(doiCiter, vocab, metaVocab, config.core)
	private val objReader = StaticObjectReader(vocab, metaVocab, lenses, pidFactory, citer)
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


	private def fetchStaticObj(hash: Sha256Sum)(using Envri): Validated[StaticObject] =
		server.access: conn ?=>
			val objIri = vocab.getStaticObject(hash)
			given GlobConn = RdfLens.global(using conn)
			objReader.fetchStaticObject(objIri)


	private def fetchStaticColl(hash: Sha256Sum)(using Envri): Validated[StaticCollection] =
		access(lenses.collectionLens):
			val collUri = vocab.getCollection(hash)
			for
				given DocConn <- lenses.documentLens
				coll <- objReader.fetchStaticColl(collUri, Some(hash))
			yield coll


	private def fetchStation(uri: Uri)(using Envri): VOE[Station] = accessMeta:
		for
			given DocConn <- lenses.documentLens
			st <- objReader.getStation(uri.toRdf)
			membs <- citer.attrProvider.getMemberships(st.org.self.uri)
		yield OrganizationExtra(st, membs)

	private def fetchOrg(uri: Uri)(using Envri): VOE[Organization] = accessMeta:
		for
			org <- objReader.getOrganization(uri.toRdf)
			membs <- citer.attrProvider.getMemberships(org.self.uri)
		yield OrganizationExtra(org, membs)

	private def fetchPerson(uri: Uri)(using Envri): Validated[PersonExtra] = accessMeta:
		for
			pers <- objReader.getPerson(uri.toRdf)
			roles <- citer.attrProvider.getPersonRoles(pers.self.uri)
		yield PersonExtra(pers, roles)

	private def access[T, C <: TriplestoreConnection](lensV: Validated[RdfLens[C]])(reader: C ?=> Validated[T]): Validated[T] =
		server.access:
			lensV.flatMap: lens =>
				reader(using lens)

	private def accessMeta[T](reader: MetaConn ?=> Validated[T])(using Envri): Validated[T] =
		access(lenses.metaInstanceLens)(reader)

	// private def readMetaRes[T, C <: MetaConn](uri: Uri)(reader: (DobjMetaReader, IRI) => C ?=> Validated[T])(using Envri): Validated[T] =
	// 	accessMeta(reader(objReader, uri.toRdf))

	private def getDefaultHtml(uri: Uri)(charset: HttpCharset): HttpResponse =
		given envri: Envri = inferEnvri(uri)
		given EnvriConfig = envries(envri)
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


	private def isObjSpec(uri: Uri): Boolean = server.access:
		hasStatement(uri.toRdf, metaVocab.hasDataLevel, null)

	private def isLabeledRes(uri: Uri): Boolean = server.access:
		hasStatement(uri.toRdf, RDFS.LABEL, null)

	private def getMarshallings(uri: Uri)(using Envri, EnvriConfig, ExecutionContext): FLMHR =

		def resourceMarshallings[T : JsonWriter](
			resId: String, resourceType: String, fetcher: Uri => Validated[T],
			pageTemplate: (T, ErrorList) => Html
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
				pageContentMarshalling.staticObjectMarshaller(() => fetchStaticObj(hash))

			case Hash.Collection(hash) =>
				pageContentMarshalling.statCollMarshaller(() => fetchStaticColl(hash))

			case UriPath("resources", "stations", stId) => resourceMarshallings(
				stId, "station", fetchStation,
				(st, errors) => views.html.StationLandingPage(st, vocab, errors)
			)

			case UriPath("resources", "organizations", orgId) => resourceMarshallings(
				orgId, "organization", fetchOrg,
				views.html.OrgLandingPage(_, _)
			)

			case UriPath("resources", "instruments", instrId) => resourceMarshallings(
				instrId, "instrument", uri => access(lenses.metaInstanceLens)(objReader.getInstrument(uri.toRdf)),
				views.html.InstrumentLandingPage(_, _)
			)

			case UriPath("resources", "people", persId) => resourceMarshallings(
				persId, "person", fetchPerson,
				views.html.PersonLandingPage(_, _)
			)(using OrganizationExtra.persExtraWriter)

			case Slash(Segment("resources", _)) if isObjSpec(uri) => oneOf(
				customJson(() => access(lenses.documentLens)(objReader.getSpecification(uri.toRdf))),
				defaultHtml(uri)
			)

			case _ if isLabeledRes(uri) => oneOf(
				customJson(() => accessMeta(getLabeledResource(uri.toRdf))),
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
