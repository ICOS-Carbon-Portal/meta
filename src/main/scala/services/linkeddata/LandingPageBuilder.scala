package se.lu.nateko.cp.meta.services.linkeddata

import scala.language.unsafeNulls

import akka.http.scaladsl.model.Uri
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.{IRI, Literal, Resource, Value, ValueFactory}
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS}
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection}
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
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
import scala.collection.mutable
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

	private def readStaticObject(hash: Sha256Sum)(using Envri): Validated[StaticObject] =
		val objectIri = vocab.getStaticObject(hash)
		fromSnapshot(objectIri, staticObjectLinks): snapshotConn =>
			given GlobConn = RdfLens.global(using snapshotConn)
			objectReader.fetchStaticObject(objectIri)

	private def readStaticCollection(hash: Sha256Sum)(using Envri): Validated[StaticCollection] =
		val collectionIri = vocab.getCollection(hash)
		fromSnapshot(collectionIri, collectionLinks(collectionIri)): snapshotConn =>
			for
				collLens <- lenses.collectionLens
				docLens <- lenses.documentLens
				collection <- objectReader.fetchStaticColl(collectionIri, Some(hash))(
					using collLens(using snapshotConn), docLens(using snapshotConn)
				)
			yield collection

	/**
	 * Reads the metadata closure of `root` into a short-lived local repository, and lets `reader`
	 * build the page from that snapshot instead of from the RDF store.
	 *
	 * The readers predate the RDF-store service and follow links one statement at a time. Against a
	 * remote repository that turns a single landing page into dozens (and, for rich data objects,
	 * hundreds) of requests. The closure is fetched in a few indexed batches instead: each batch
	 * obtains all triples for a small `VALUES` frontier, plus the inverse links the readers use,
	 * which is much cheaper for the triplestore than one deeply nested cross-graph traversal. The
	 * snapshot retains the named graphs of the statements, so the readers can keep using their
	 * normal graph lenses, without making any further network requests.
	 */
	private def fromSnapshot[T](root: IRI, links: LinkPolicy)(
		reader: (TriplestoreConnection & SparqlRunner) => Validated[T]
	): Validated[T] =
		val snapshot = server.access: conn ?=>
			metadataSnapshot(conn, root, links)
		try
			Rdf4jInstanceServer(snapshot).access: snapshotConn ?=>
				reader(snapshotConn)
		finally snapshot.shutDown()

	private def metadataSnapshot(
		conn: TriplestoreConnection & SparqlRunner,
		root: IRI,
		links: LinkPolicy
	): Repository =
		val snapshot = SailRepository(MemoryStore())
		snapshot.init()
		try
			Using.resource(snapshot.getConnection()): target =>
				val seen = mutable.Set.empty[IRI]
				var frontier = Set(root)
				var depth = 0
				while frontier.nonEmpty && depth <= links.maxDepth do
					val batch = frontier.diff(seen)
					seen ++= batch
					val statements = fetchBatch(conn, target, batch, links.inverse)
					val predicatesOf = statements
						.collect:
							case (subject: IRI, predicate, _, _) if batch.contains(subject) => subject -> predicate
						.groupMap(_._1)(_._2)
						.withDefaultValue(Nil)

					val forward = statements.collect:
						case (subject: IRI, predicate, obj: IRI, _) if
							batch.contains(subject) && links.follows(subject, predicate, predicatesOf(subject)) =>
							obj
					val backward = statements.collect:
						case (subject: IRI, predicate, obj: IRI, _) if
							batch.contains(obj) && links.inverse.contains(predicate) =>
							subject
					frontier = (forward ++ backward).toSet.diff(seen)
					depth += 1
			snapshot
		catch
			case err: Throwable =>
				snapshot.shutDown()
				throw err

	private val staticObjectForwardLinks: Set[IRI] = Set(
			metaVocab.hasObjectSpec,
			metaVocab.wasSubmittedBy,
			metaVocab.wasAcquiredBy,
			metaVocab.wasProducedBy,
			metaVocab.hasAssociatedProject,
			metaVocab.hasDataTheme,
			metaVocab.hasFormat,
			metaVocab.hasEncoding,
			metaVocab.containsDataset,
			metaVocab.hasDocumentationObject,
			metaVocab.hasVariable,
			metaVocab.hasColumn,
			metaVocab.hasValueType,
			metaVocab.hasQuantityKind,
			metaVocab.prov.wasAssociatedWith,
			metaVocab.wasPerformedBy,
			metaVocab.wasParticipatedInBy,
			metaVocab.wasHostedBy,
			metaVocab.prov.hadPrimarySource,
			metaVocab.dcterms.creator,
			RDFS.SEEALSO,
			metaVocab.hasSpatialCoverage,
			metaVocab.hasResponsibleOrganization,
			metaVocab.hasAssociatedNetwork,
			metaVocab.hasFunding,
			metaVocab.hasFunder,
			metaVocab.operatesOn,
			metaVocab.hasEcosystemType,
			metaVocab.hasClimateZone,
			metaVocab.wasPerformedWith,
			metaVocab.wasPerformedAt,
			metaVocab.hasSamplingPoint,
			metaVocab.hasActualVariable,
			metaVocab.hasInstrumentOwner,
			metaVocab.hasVendor,
			metaVocab.hasInstrumentComponent,
			metaVocab.ssn.hasDeployment,
			metaVocab.ssn.forProperty,
			metaVocab.hasWebpageElements,
			metaVocab.hasLinkbox
		)
	private val collectionForwardLinks: Set[IRI] = Set(
			metaVocab.dcterms.creator,
			RDFS.SEEALSO,
			metaVocab.hasSpatialCoverage,
			metaVocab.hasWebpageElements,
			metaVocab.hasLinkbox,
			metaVocab.isNextVersionOf,
			metaVocab.wasSubmittedBy,
			metaVocab.prov.wasAssociatedWith
		)

	private val staticObjectLinks = LinkPolicy(
		maxDepth = StaticObjectLinkDepth,
		inverse = Set(
			metaVocab.dcterms.hasPart,
			metaVocab.isNextVersionOf,
			metaVocab.atOrganization,
			metaVocab.ssn.hasDeployment
		),
		follows = (_, predicate, subjectPredicates) =>
			staticObjectForwardLinks.contains(predicate) ||
				predicate.stringValue.startsWith(RDF.NAMESPACE + "_") ||
				(predicate === metaVocab.dcterms.hasPart && subjectPredicates.contains(metaVocab.isNextVersionOf))
	)

	/**
	 * The collection page is made of the collection itself, its members, its creator organization,
	 * its documentation and coverage, plus its neighbouring versions and parent collections.
	 * `dcterms:hasPart` is followed out of the collection the page is about, and out of the plain
	 * collections version chains are made of, but not out of the parent collections: their other
	 * members are of no interest, and there can be very many of them.
	 */
	private def collectionLinks(collectionIri: IRI) = LinkPolicy(
		maxDepth = CollectionLinkDepth,
		inverse = Set(metaVocab.dcterms.hasPart, metaVocab.isNextVersionOf),
		follows = (subject, predicate, subjectPredicates) =>
			collectionForwardLinks.contains(predicate) ||
				(predicate === metaVocab.dcterms.hasPart &&
					(subject === collectionIri || subjectPredicates.contains(metaVocab.isNextVersionOf)))
	)

	private def fetchBatch(
		conn: SparqlRunner,
		target: RepositoryConnection,
		batch: Set[IRI],
		inverseLinks: Set[IRI]
	): IndexedSeq[(Resource, IRI, Value, Resource)] =
		Using.resource(conn.evaluateTupleQuery(batchQuery(batch, inverseLinks))): rows =>
			rows.map: bindings =>
				val subject = bindings.getValue("subject").asInstanceOf[Resource]
				val predicate = bindings.getValue("predicate").asInstanceOf[IRI]
				val obj = bindings.getValue("object")
				val context = bindings.getValue("context").asInstanceOf[Resource]
				target.add(subject, predicate, obj, context)
				(subject, predicate, obj, context)
			.toIndexedSeq

	private def batchQuery(batch: Set[IRI], inverseLinks: Set[IRI]): String =
		val values = batch.iterator.map(sparqlIri).mkString(" ")
		val inversePredicates = inverseLinks.iterator.map(sparqlIri).mkString(" ")
		s"""SELECT DISTINCT ?subject ?predicate ?object ?context
			|WHERE {
			|  {
			|    VALUES ?subject { $values }
			|    GRAPH ?context { ?subject ?predicate ?object }
			|  } UNION {
			|    VALUES ?object { $values }
			|    VALUES ?predicate { $inversePredicates }
			|    GRAPH ?context { ?subject ?predicate ?object }
			|  }
			|}""".stripMargin

	private def sparqlIri(iri: IRI): String = s"<${iri.stringValue}>"

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
	// Longest metadata chain currently rendered is object -> spec -> dataset -> variable -> value type -> quantity kind.
	private val StaticObjectLinkDepth = 5
	// Longest chain is collection -> next version -> its members -> submission -> submitter -> webpage elements -> link box.
	private val CollectionLinkDepth = 6

	/**
	 * The shape of the metadata closure a landing page needs: which properties to follow out of the
	 * resources fetched so far, which to follow into them, and how long a chain of them can get.
	 *
	 * `follows` is also given the subject and its properties, because whether a link is worth
	 * following can depend on what the subject turned out to be: `dcterms:hasPart`, for one, leads
	 * to all the members of a collection, which are only wanted for some of the collections met.
	 */
	private class LinkPolicy(
		val maxDepth: Int,
		val inverse: Set[IRI],
		/** (subject, predicate, the properties the subject turned out to have) */
		val follows: (IRI, IRI, Seq[IRI]) => Boolean
	)

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
