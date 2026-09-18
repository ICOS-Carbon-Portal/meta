package se.lu.nateko.cp.meta.services.linkeddata

import scala.language.unsafeNulls

import akka.http.scaladsl.model.Uri
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.{IRI, Literal, ValueFactory}
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS}
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.api.*
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.{Rdf4jInstanceServer, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.services.attribution.AttributionProvider
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataClient
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.views.ResourceViewInfo
import se.lu.nateko.cp.meta.views.ResourceViewInfo.PropValue

import java.net.{URI => JavaUri}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Using}

/**
 * Repository-backed builder for the data consumed by linked-data landing pages.
 *
 * This component owns graph selection and RDF-to-domain projection. HTTP content negotiation and
 * Twirl rendering deliberately remain outside it.
 */
final class LandingPageBuilder(
	repo: Repository,
	vocab: CpVocab,
	metaVocab: CpmetaVocab,
	lenses: RdfLenses,
	pidFactory: PidFactory,
	derivedMetadata: DerivedMetadataClient
)(using ExecutionContext):

	import LandingPageBuilder.*
	import RdfLens.{DocConn, GlobConn, MetaConn}
	import se.lu.nateko.cp.meta.instanceserver.StatementSource.{getLabeledResource, hasStatement}

	private given ValueFactory = repo.getValueFactory
	private val server = new Rdf4jInstanceServer(repo)
	private val attribution = new AttributionProvider(vocab, metaVocab)
	private val objectReader = StaticObjectReader(vocab, metaVocab, lenses, pidFactory, None)

	def staticObject(hash: Sha256Sum)(using Envri): Validated[StaticObject] = readStaticObject(hash)

	def staticCollection(hash: Sha256Sum)(using Envri): Validated[StaticCollection] = readStaticCollection(hash)

	def staticObjectWithDerived(uri: Uri, hash: Sha256Sum)(using Envri): Future[Validated[StaticObject]] =
		enrich(staticObject(hash))(derivedMetadata.enrich(new JavaUri(uri.toString), _))

	def staticCollectionWithDerived(uri: Uri, hash: Sha256Sum)(using Envri): Future[Validated[StaticCollection]] =
		enrich(staticCollection(hash))(derivedMetadata.enrich(new JavaUri(uri.toString), _))

	def station(uri: Uri)(using Envri): Validated[OrganizationExtra[Station]] = accessMeta:
		for
			given DocConn <- lenses.documentLens
			station <- objectReader.getStation(uri.toRdf)
			memberships <- attribution.getMemberships(station.org.self.uri)
		yield OrganizationExtra(station, memberships)

	def organization(uri: Uri)(using Envri): Validated[OrganizationExtra[Organization]] = accessMeta:
		for
			organization <- objectReader.getOrganization(uri.toRdf)
			memberships <- attribution.getMemberships(organization.self.uri)
		yield OrganizationExtra(organization, memberships)

	def instrument(uri: Uri)(using Envri): Validated[Instrument] =
		access(lenses.metaInstanceLens)(objectReader.getInstrument(uri.toRdf))

	def person(uri: Uri)(using Envri): Validated[PersonExtra] = accessMeta:
		for
			person <- objectReader.getPerson(uri.toRdf)
			roles <- attribution.getPersonRoles(person.self.uri)
		yield PersonExtra(person, roles)

	def specification(uri: Uri)(using Envri): Validated[DataObjectSpec] =
		access(lenses.documentLens)(objectReader.getSpecification(uri.toRdf))

	def labeledResource(uri: Uri)(using Envri): Validated[UriResource] =
		accessMeta(getLabeledResource(uri.toRdf))

	def isObjectSpecification(uri: Uri): Boolean = server.access:
		hasStatement(uri.toRdf, metaVocab.hasDataLevel, null)

	def isLabeledResource(uri: Uri): Boolean = server.access:
		hasStatement(uri.toRdf, RDFS.LABEL, null)

	def genericResource(uri: Uri): Try[ResourceViewInfo] = Using.Manager: use =>
		val conn = use(repo.getConnection())

		val propInfos = use(
			conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceViewInfoQuery(uri)).evaluate().asCloseableIterator
		).map: bindings =>
			val property = getOptUriResource(bindings, "prop", "propLabel")
			val value: Option[PropValue] = bindings.getValue("val") match
				case iri: IRI => Some(Left(UriResource(iri.toJava, getOptLiteral(bindings, "valLabel"), Nil)))
				case literal: Literal => Some(Right(literal.stringValue))
				case _ => None
			property zip value
		.flatten.take(ResultLimit).toIndexedSeq

		val usageInfos = use(
			conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceUsageInfoQuery(uri)).evaluate().asCloseableIterator
		).map: bindings =>
			getOptUriResource(bindings, "obj", "objLabel") zip
				getOptUriResource(bindings, "prop", "propLabel")
		.flatten.take(ResultLimit).toIndexedSeq

		val resourceUri = JavaUri.create(uri.toString)
		val seed = ResourceViewInfo(UriResource(resourceUri, None, Nil), Nil, Nil, usageInfos)

		propInfos.foldLeft(seed): (acc, propertyAndValue) =>
			propertyAndValue match
				case (UriResource(propertyUri, _, _), Right(value)) if propertyUri === RDFS.LABEL =>
					acc.copy(res = acc.res.copy(label = Some(value)))
				case (UriResource(propertyUri, _, _), Right(value)) if propertyUri === RDFS.COMMENT =>
					acc.copy(res = acc.res.copy(comments = acc.res.comments :+ value))
				case (UriResource(propertyUri, _, _), Left(rdfType)) if propertyUri === RDF.TYPE =>
					acc.copy(types = rdfType :: acc.types)
				case _ =>
					acc.copy(propValues = propertyAndValue :: acc.propValues)

	private def readStaticObject(hash: Sha256Sum)(using Envri): Validated[StaticObject] = server.access: conn ?=>
		val objectIri = vocab.getStaticObject(hash)
		given GlobConn = RdfLens.global(using conn)
		objectReader.fetchStaticObject(objectIri)

	private def readStaticCollection(hash: Sha256Sum)(using Envri): Validated[StaticCollection] =
		access(lenses.collectionLens):
			val collectionUri = vocab.getCollection(hash)
			for
				given DocConn <- lenses.documentLens
				collection <- objectReader.fetchStaticColl(collectionUri, Some(hash))
			yield collection

	private def enrich[T](parsed: Validated[T])(fetch: T => Future[T]): Future[Validated[T]] =
		parsed.result.fold(Future.successful(new Validated[T](None, parsed.errors))): item =>
			fetch(item)
				.map(enriched => new Validated(Some(enriched), parsed.errors))
				.recover { case err =>
					parsed.withExtraError(s"Could not fetch derived metadata from rdfStore: ${err.getMessage}")
				}

	private def access[T, C <: TriplestoreConnection](
		lens: Validated[RdfLens[C]]
	)(reader: C ?=> Validated[T]): Validated[T] = server.access:
		lens.flatMap: connection =>
			reader(using connection)

	private def accessMeta[T](reader: MetaConn ?=> Validated[T])(using Envri): Validated[T] =
		access(lenses.metaInstanceLens)(reader)

object LandingPageBuilder:
	private val ResultLimit = 500

	private def getOptUriResource(bindings: BindingSet, valueName: String, labelName: String): Option[UriResource] =
		bindings.getValue(valueName) match
			case iri: IRI => Some(UriResource(iri.toJava, getOptLiteral(bindings, labelName), Nil))
			case _ => None

	private def getOptLiteral(bindings: BindingSet, valueName: String): Option[String] =
		bindings.getValue(valueName) match
			case null => None
			case literal: Literal => Some(literal.stringValue)
			case _ => None

	private def resourceViewInfoQuery(resource: Uri): String =
		s"""SELECT ?prop ?propLabel ?val ?valLabel
		|WHERE{
		|	<${resource.toString}> ?prop ?val .
		|	OPTIONAL {?prop rdfs:label ?propLabel}
		|	OPTIONAL {?val rdfs:label ?valLabel}
		|}""".stripMargin

	private def resourceUsageInfoQuery(resource: Uri): String =
		s"""SELECT ?obj ?objLabel ?prop ?propLabel
		|WHERE{
		|	?obj ?prop <${resource.toString}> .
		|	OPTIONAL {?obj rdfs:label ?objLabel}
		|	OPTIONAL {?prop rdfs:label ?propLabel}
		|}""".stripMargin
