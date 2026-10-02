package se.lu.nateko.cp.meta.services.linkeddata

import scala.language.unsafeNulls

import akka.http.scaladsl.model.Uri
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.{IRI, Literal, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS}
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.api.*
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.instanceserver.{Rdf4jInstanceServer, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.services.citation.CitationMaker
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.views.ResourceViewInfo
import se.lu.nateko.cp.meta.views.ResourceViewInfo.PropValue

import java.net.{URI => JavaUri}
import scala.collection.mutable
import scala.util.{Try, Using}

/**
 * Repository-backed builder for the data consumed by linked-data landing pages.
 *
 * This component owns graph selection and RDF-to-domain projection. HTTP content negotiation and
 * Twirl rendering deliberately remain outside it.
 */
final class LandingPageLoader(
	repo: Repository,
	vocab: CpVocab,
	metaVocab: CpmetaVocab,
	lenses: RdfLenses,
	pidFactory: HandleNetClient.PidFactory,
	citer: CitationMaker
) {

	import LandingPageLoader.*
	import RdfLens.{DocConn, GlobConn, MetaConn}
	import se.lu.nateko.cp.meta.instanceserver.StatementSource.{getLabeledResource, hasStatement}

	private given ValueFactory = repo.getValueFactory
	private val server = new Rdf4jInstanceServer(repo)
	private val objectReader = StaticObjectReader(vocab, metaVocab, lenses, pidFactory, citer)

	def staticObject(hash: Sha256Sum)(using Envri): Validated[StaticObject] = readStaticObject(hash)

	def staticCollection(hash: Sha256Sum)(using Envri): Validated[StaticCollection] = readStaticCollection(hash)

	def station(uri: Uri)(using Envri): Validated[OrganizationExtra[Station]] = accessMeta {
		for {
			given DocConn <- lenses.documentLens
			station <- objectReader.getStation(uri.toRdf)
			memberships <- citer.attrProvider.getMemberships(station.org.self.uri)
		} yield OrganizationExtra(station, memberships)
	}

	def organization(uri: Uri)(using Envri): Validated[OrganizationExtra[Organization]] = accessMeta {
		for {
			organization <- objectReader.getOrganization(uri.toRdf)
			memberships <- citer.attrProvider.getMemberships(organization.self.uri)
		} yield OrganizationExtra(organization, memberships)
	}

	def instrument(uri: Uri)(using Envri): Validated[Instrument] =
		access(lenses.metaInstanceLens)(objectReader.getInstrument(uri.toRdf))

	def person(uri: Uri)(using Envri): Validated[PersonExtra] = accessMeta {
		for {
			person <- objectReader.getPerson(uri.toRdf)
			roles <- citer.attrProvider.getPersonRoles(person.self.uri)
		} yield PersonExtra(person, roles)
	}

	def specification(uri: Uri)(using Envri): Validated[DataObjectSpec] =
		access(lenses.documentLens)(objectReader.getSpecification(uri.toRdf))

	def labeledResource(uri: Uri)(using Envri): Validated[UriResource] =
		accessMeta(getLabeledResource(uri.toRdf))

	def isObjectSpecification(uri: Uri): Boolean = server.access {
		hasStatement(uri.toRdf, metaVocab.hasDataLevel, null)
	}

	def isLabeledResource(uri: Uri): Boolean = server.access {
		hasStatement(uri.toRdf, RDFS.LABEL, null)
	}

	def genericResource(uri: Uri): Try[ResourceViewInfo] = Using.Manager { use =>
		val conn = use(repo.getConnection())

		val propInfos = use(
			conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceViewInfoQuery(uri)).evaluate().asCloseableIterator
		).map { bindings =>
			val property = getOptUriResource(bindings, "prop", "propLabel")
			val value: Option[PropValue] = bindings.getValue("val") match {
				case iri: IRI => Some(Left(UriResource(iri.toJava, getOptLiteral(bindings, "valLabel"), Nil)))
				case literal: Literal => Some(Right(literal.stringValue))
				case _ => None
			}
			property zip value
		}.flatten.take(ResultLimit).toIndexedSeq

		val usageInfos = use(
			conn.prepareTupleQuery(QueryLanguage.SPARQL, resourceUsageInfoQuery(uri)).evaluate().asCloseableIterator
		).map { bindings =>
			getOptUriResource(bindings, "obj", "objLabel") zip
				getOptUriResource(bindings, "prop", "propLabel")
		}.flatten.take(ResultLimit).toIndexedSeq

		val resourceUri = JavaUri.create(uri.toString)
		val seed = ResourceViewInfo(UriResource(resourceUri, None, Nil), Nil, Nil, usageInfos)

		propInfos.foldLeft(seed) { (acc, propertyAndValue) =>
			propertyAndValue match {
				case (UriResource(propertyUri, _, _), Right(value)) if propertyUri === RDFS.LABEL =>
					acc.copy(res = acc.res.copy(label = Some(value)))
				case (UriResource(propertyUri, _, _), Right(value)) if propertyUri === RDFS.COMMENT =>
					acc.copy(res = acc.res.copy(comments = acc.res.comments :+ value))
				case (UriResource(propertyUri, _, _), Left(rdfType)) if propertyUri === RDF.TYPE =>
					acc.copy(types = rdfType :: acc.types)
				case _ =>
					acc.copy(propValues = propertyAndValue :: acc.propValues)
			}
		}
	}

	private def readStaticObject(hash: Sha256Sum)(using Envri): Validated[StaticObject] = cachedAccess { conn ?=>
		val objectIri = vocab.getStaticObject(hash)
		given GlobConn = RdfLens.global(using conn)
		objectReader.fetchStaticObject(objectIri)
	}

	private def readStaticCollection(hash: Sha256Sum)(using Envri): Validated[StaticCollection] =
		access(lenses.collectionLens) {
			val collectionUri = vocab.getCollection(hash)
			for {
				given DocConn <- lenses.documentLens
				collection <- objectReader.fetchStaticColl(collectionUri, Some(hash))
			} yield collection
		}

	private def access[T, C <: TriplestoreConnection](
		lens: Validated[RdfLens[C]]
	)(reader: C ?=> Validated[T]): Validated[T] = cachedAccess {
		lens.flatMap { connection =>
			reader(using connection)
		}
	}

	/** A read in which every statement is fetched from the RDF store at most once */
	private def cachedAccess[T](read: TriplestoreConnection ?=> T): T = server.access { conn ?=>
		read(using CachingConnection(conn))
	}

	private def accessMeta[T](reader: MetaConn ?=> Validated[T])(using Envri): Validated[T] =
		access(lenses.metaInstanceLens)(reader)
}

object LandingPageLoader {
	private val ResultLimit = 500

	private def getOptUriResource(bindings: BindingSet, valueName: String, labelName: String): Option[UriResource] =
		bindings.getValue(valueName) match {
			case iri: IRI => Some(UriResource(iri.toJava, getOptLiteral(bindings, labelName), Nil))
			case _ => None
		}

	private def getOptLiteral(bindings: BindingSet, valueName: String): Option[String] =
		bindings.getValue(valueName) match {
			case null => None
			case literal: Literal => Some(literal.stringValue)
			case _ => None
		}

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
}

/**
 * A view of a triplestore connection that remembers what has been read through it, meant to be used
 * for the duration of one read-only operation, such as building a landing page.
 *
 * The readers look up the properties of a resource one by one, and often read the same resource many
 * times over (an organization, for example, is the submitter, the host and the responsible
 * organization at once). So, the first time a subject is looked at, all of its statements are read
 * at once, and the later lookups of its properties are answered from memory. Subjects with too many
 * statements to keep are not prefetched; lookups on them, as well as those without a subject, are
 * remembered as they are made.
 *
 * Views on other RDF graphs, made with `withContexts`, share the cache, keyed by their read contexts.
 */
private final class CachingConnection(
	inner: TriplestoreConnection, cache: CachingConnection.Cache
) extends TriplestoreConnection:
	import CachingConnection.*

	def this(inner: TriplestoreConnection) = this(inner, CachingConnection.Cache())

	override def primaryContext: IRI = inner.primaryContext
	override def readContexts: Seq[IRI] = inner.readContexts
	override def factory: ValueFactory = inner.factory

	override def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection =
		CachingConnection(inner.withContexts(primary, read), cache)

	override def close(): Unit = inner.close()

	override def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement] =
		val statements = subjectStatements(subject) match
			case Some(all) => all.filter(matches(predicate, obj))
			case None => cache.lookups.getOrElseUpdate(
				(readContexts, subject, predicate, obj),
				Using.resource(inner.getStatements(subject, predicate, obj))(_.toIndexedSeq)
			)
		CloseableIterator.Wrap(statements.iterator, () => ())

	override def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean =
		subjectStatements(subject) match
			case Some(all) => all.exists(matches(predicate, obj))
			case None => inner.hasStatement(subject, predicate, obj)

	private def subjectStatements(subject: IRI | Null): Option[IndexedSeq[Statement]] =
		if subject == null then None
		else cache.subjects.getOrElseUpdate(
			(readContexts, subject),
			Using.resource(inner.getStatements(subject, null, null)): iter =>
				val statements = iter.take(MaxSubjectStatements + 1).toIndexedSeq
				Option.when(statements.length <= MaxSubjectStatements)(statements)
		)

end CachingConnection

private object CachingConnection:
	private val MaxSubjectStatements = 1000

	final class Cache:
		val subjects = mutable.HashMap.empty[(Seq[IRI], IRI), Option[IndexedSeq[Statement]]]
		val lookups = mutable.HashMap.empty[(Seq[IRI], IRI | Null, IRI | Null, Value | Null), IndexedSeq[Statement]]

	private def matches(predicate: IRI | Null, obj: Value | Null)(st: Statement): Boolean =
		(predicate == null || predicate == st.getPredicate) && (obj == null || obj == st.getObject)
