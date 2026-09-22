package se.lu.nateko.cp.meta.services.linkeddata

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.{IRI, Resource, Value}
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS}
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection}
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import se.lu.nateko.cp.meta.api.{RdfLenses, SparqlRunner}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.instanceserver.{Rdf4jInstanceServer, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.services.upload.StaticObjectReader
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.*

import scala.collection.mutable
import scala.util.Using

/** Builds collection landing-page data from a bounded, role-aware RDF snapshot. */
private[linkeddata] final class CollectionLandingPageBuilder(
	repo: Repository,
	vocab: CpVocab,
	metaVocab: CpmetaVocab,
	lenses: RdfLenses,
	objectReader: StaticObjectReader
):

	import CollectionLandingPageBuilder.*

	private val server = new Rdf4jInstanceServer(repo)

	def staticCollection(hash: Sha256Sum)(using Envri): Validated[StaticCollection] =
		val collectionIri = vocab.getCollection(hash)
		val snapshot = server.access: conn ?=>
			metadataSnapshot(conn, collectionIri)
		try
			Rdf4jInstanceServer(snapshot).access: snapshotConn ?=>
				for
					collLens <- lenses.collectionLens
					docLens <- lenses.documentLens
					collection <- objectReader.fetchStaticColl(collectionIri, Some(hash))(
						using collLens(using snapshotConn), docLens(using snapshotConn)
					)
				yield collection
		finally snapshot.shutDown()

	/**
	 * Collection members are leaves of this crawl. In particular, their inverse `dcterms:hasPart`
	 * links must not be followed: doing so connects a collection page to every other collection its
	 * members occur in, and from there potentially to another large set of members and versions.
	 *
	 * Version candidates are different from ordinary members. The collection reader checks their
	 * completeness and moratorium status and recursively looks for newer versions, so their
	 * submissions and inverse version links are retained explicitly.
	 */
	private def metadataSnapshot(
		conn: TriplestoreConnection & SparqlRunner,
		collectionIri: IRI
	): Repository =
		val snapshot = SailRepository(MemoryStore())
		snapshot.init()
		try
			Using.resource(snapshot.getConnection()): target =>
				val seenRoles = mutable.Map.empty[IRI, Set[NodeRole]]
				var frontier: Map[IRI, Set[NodeRole]] = Map(collectionIri -> Set(NodeRole.Root))

				while frontier.nonEmpty do
					val batch = frontier.flatMap: (iri, roles) =>
						val unseen = roles -- seenRoles.getOrElse(iri, Set.empty)
						Option.when(unseen.nonEmpty)(iri -> unseen)
					seenRoles ++= batch.map: (iri, roles) =>
						iri -> (seenRoles.getOrElse(iri, Set.empty) ++ roles)

					val statements = batch.grouped(BatchSize)
						.flatMap(chunk => fetchBatch(conn, target, chunk.toMap))
						.toIndexedSeq
					val statementsOf = statements.collect:
						case (subject: IRI, predicate, obj, _) if batch.contains(subject) =>
							subject -> (predicate, obj)
					.groupMap(_._1)(_._2).withDefaultValue(Nil)

					val next = mutable.Map.empty[IRI, Set[NodeRole]]
					def enqueue(iri: IRI, role: NodeRole): Unit =
						next.update(iri, next.getOrElse(iri, Set.empty) + role)

					statements.foreach:
						case (subject: IRI, predicate, obj: IRI, _) if batch.contains(subject) =>
							val roles = batch(subject)
							if roles.contains(NodeRole.Root) then
								if predicate === metaVocab.dcterms.hasPart then enqueue(obj, NodeRole.Member)
								else if rootDetailLinks.contains(predicate) then enqueue(obj, NodeRole.Detail)
							if roles.contains(NodeRole.Version) then
								if predicate === metaVocab.dcterms.hasPart && isPlainCollection(statementsOf(subject)) then
									enqueue(obj, NodeRole.Version)
								else if predicate === metaVocab.wasSubmittedBy then enqueue(obj, NodeRole.Detail)
							if roles.contains(NodeRole.Detail) && nestedDetailLinks.contains(predicate) then
								enqueue(obj, NodeRole.Detail)
						case (subject: IRI, predicate, obj: IRI, _) if batch.contains(obj) =>
							val roles = batch(obj)
							if predicate === metaVocab.dcterms.hasPart then
								if roles.contains(NodeRole.Root) then enqueue(subject, NodeRole.Parent)
								else if roles.contains(NodeRole.Parent) then enqueue(subject, NodeRole.ParentWrapper)
							else if predicate === metaVocab.isNextVersionOf && (
								roles.contains(NodeRole.Root) || roles.contains(NodeRole.Version)
							)
							then enqueue(subject, NodeRole.Version)
						case _ => ()
					frontier = next.toMap
			snapshot
		catch
			case err: Throwable =>
				snapshot.shutDown()
				throw err

	private def fetchBatch(
		conn: SparqlRunner,
		target: RepositoryConnection,
		batch: Map[IRI, Set[NodeRole]]
	): IndexedSeq[(Resource, IRI, Value, Resource)] =
		Using.resource(conn.evaluateTupleQuery(batchQuery(batch))): rows =>
			rows.map: bindings =>
				val subject = bindings.getValue("subject").asInstanceOf[Resource]
				val predicate = bindings.getValue("predicate").asInstanceOf[IRI]
				val obj = bindings.getValue("object")
				val context = bindings.getValue("context").asInstanceOf[Resource]
				target.add(subject, predicate, obj, context)
				(subject, predicate, obj, context)
			.toIndexedSeq

	private def batchQuery(batch: Map[IRI, Set[NodeRole]]): String =
		def subjectsWith(role: NodeRole): Iterable[IRI] = batch.collect:
			case (iri, roles) if roles.contains(role) => iri
		val fullSubjects = batch.collect:
			case (iri, roles) if roles.contains(NodeRole.Root) || roles.contains(NodeRole.Detail) => iri
		val forwardBranches = Seq(
			Option.when(fullSubjects.nonEmpty)(queryBranch("subject", fullSubjects)),
			predicateQueryBranch(subjectsWith(NodeRole.Member), memberPredicates),
			predicateQueryBranch(subjectsWith(NodeRole.Parent), parentPredicates),
			predicateQueryBranch(subjectsWith(NodeRole.ParentWrapper), parentPredicates),
			predicateQueryBranch(subjectsWith(NodeRole.Version), versionPredicates)
		).flatten
		val parentObjects = batch.collect:
			case (iri, roles) if roles.contains(NodeRole.Root) || roles.contains(NodeRole.Parent) => iri
		val versionObjects = batch.collect:
			case (iri, roles) if roles.contains(NodeRole.Root) || roles.contains(NodeRole.Version) => iri
		val inverseParents = Option.when(parentObjects.nonEmpty):
			inverseQueryBranch(parentObjects, Set(metaVocab.dcterms.hasPart))
		val inverseVersions = Option.when(versionObjects.nonEmpty):
			inverseQueryBranch(versionObjects, Set(metaVocab.isNextVersionOf))
		val branches = (forwardBranches ++ inverseParents.toSeq ++ inverseVersions.toSeq).mkString(" UNION ")
		s"""SELECT DISTINCT ?subject ?predicate ?object ?context
			|WHERE {
			|  $branches
			|}""".stripMargin

	private def queryBranch(variable: String, iris: Iterable[IRI]): String =
		s"""{
			|    VALUES ?$variable { ${iris.iterator.map(sparqlIri).mkString(" ")} }
			|    GRAPH ?context { ?subject ?predicate ?object }
			|  }""".stripMargin

	private def predicateQueryBranch(subjects: Iterable[IRI], predicates: Set[IRI]): Option[String] =
		Option.when(subjects.nonEmpty):
			s"""{
				|    VALUES ?subject { ${subjects.iterator.map(sparqlIri).mkString(" ")} }
				|    VALUES ?predicate { ${predicates.iterator.map(sparqlIri).mkString(" ")} }
				|    GRAPH ?context { ?subject ?predicate ?object }
				|  }""".stripMargin

	private def inverseQueryBranch(objects: Iterable[IRI], predicates: Set[IRI]): String =
		s"""{
			|    VALUES ?object { ${objects.iterator.map(sparqlIri).mkString(" ")} }
			|    VALUES ?predicate { ${predicates.iterator.map(sparqlIri).mkString(" ")} }
			|    GRAPH ?context { ?subject ?predicate ?object }
			|  }""".stripMargin

	private def isPlainCollection(subjectStatements: Seq[(IRI, Value)]): Boolean =
		subjectStatements.contains(RDF.TYPE -> metaVocab.plainCollectionClass)

	private def sparqlIri(iri: IRI): String = s"<${iri.stringValue}>"

	private val rootDetailLinks: Set[IRI] = Set(
		metaVocab.dcterms.creator,
		RDFS.SEEALSO,
		metaVocab.hasSpatialCoverage
	)
	private val nestedDetailLinks: Set[IRI] = Set(
		metaVocab.hasWebpageElements,
		metaVocab.hasLinkbox,
		metaVocab.prov.wasAssociatedWith
	)
	private val memberPredicates: Set[IRI] = Set(
		RDF.TYPE,
		metaVocab.hasSha256sum,
		metaVocab.hasName,
		metaVocab.dcterms.title
	)
	private val parentPredicates: Set[IRI] = Set(
		RDF.TYPE,
		metaVocab.dcterms.title,
		metaVocab.isNextVersionOf
	)
	private val versionPredicates: Set[IRI] = Set(
		RDF.TYPE,
		metaVocab.hasSizeInBytes,
		metaVocab.dcterms.hasPart,
		metaVocab.wasSubmittedBy
	)

private object CollectionLandingPageBuilder:
	// Bounds each SPARQL VALUES clause; large collections still produce complete results over multiple queries.
	private val BatchSize = 250

	private enum NodeRole:
		case Root, Member, Parent, ParentWrapper, Version, Detail
