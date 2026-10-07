package se.lu.nateko.cp.meta.services.sync

import scala.language.unsafeNulls

import akka.NotUsed
import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.scaladsl.Source
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS}
import org.eclipse.rdf4j.model.{BNode, IRI, Resource, Value}
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.OntoConstants.CpmetaPrefix
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlRunner}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.*

import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * Kinds of resources to synchronize. Instances of `rootClasses` (or of their subclasses, according to the
 * ontologies in the local store) are the roots; `depth` is how many links away from a root (within the same
 * named graph) the associated resources may be.
 */
enum SyncKind(val rootClasses: Seq[String], val depth: Int):
	// acquisition, production, submission, spatial coverage, variable info; contributor lists of productions
	case DataObjects extends SyncKind(SyncKind.cpmeta("StaticObject", "DataObject", "DocumentObject"), 2)
	// spatial coverage, contributor lists
	case Collections extends SyncKind(SyncKind.cpmeta("PlainCollection", "Collection"), 1)
	// memberships
	case People extends SyncKind(SyncKind.cpmeta("Person"), 1)
	// fundings, webpage elements and their links
	case Stations extends SyncKind(SyncKind.cpmeta("Station") :+ (CpmetaVocab.SitesPrefix + "Station"), 2)
	// dataset specs, their columns/variables and the value types of those
	case ObjectSpecs extends SyncKind(SyncKind.cpmeta("DataObjectSpec", "SimpleObjectSpec"), 3)

object SyncKind:
	private def cpmeta(classNames: String*): Seq[String] = classNames.map(CpmetaPrefix + _)

	def parse(name: String): Option[SyncKind] = values.find(_.toString.equalsIgnoreCase(name.trim))

final case class SyncProgress(
	kind: SyncKind,
	batches: Long = 0,
	roots: Long = 0,
	missingRemotely: Long = 0,
	added: Long = 0,
	removed: Long = 0
):
	def +(batch: BatchResult) = copy(
		batches = batches + 1,
		roots = roots + batch.roots,
		missingRemotely = missingRemotely + batch.missingRemotely,
		added = added + batch.added,
		removed = removed + batch.removed
	)

	override def toString = s"$kind: $roots roots in $batches batches, $missingRemotely missing remotely, " +
		s"$added statements added, $removed statements removed"

final case class BatchResult(roots: Int, missingRemotely: Int, added: Int, removed: Int)

/**
 * Streams known (locally present) resources of the requested kinds, fetches all statements possibly associated
 * with them from a remote meta SPARQL endpoint, and updates the local repository accordingly.
 * Statements are only ever added, unless `prune` is set, in which case local statements about a subject are
 * removed if the remote endpoint reported that subject (in the same graph), but not the statement.
 * Blank nodes are ignored, as they cannot be matched between the repositories.
 * The updates bypass the RDF log; they are lost if the store is later restored from the log.
 */
final class RemoteMetaSync(
	local: Repository,
	remote: SparqlRunner,
	prune: Boolean,
	batchSize: Int = 50,
	fetchParallelism: Int = 2
)(using system: ActorSystem):
	import RemoteMetaSync.*
	private given ExecutionContext = system.dispatcher

	def run(kinds: Seq[SyncKind]): Source[SyncProgress, NotUsed] = Source(kinds).flatMapConcat(syncKind)

	def syncKind(kind: SyncKind): Source[SyncProgress, NotUsed] =
		rootsOf(kind)
			.grouped(batchSize)
			.mapAsync(fetchParallelism): batch =>
				val fetchRemote = (query: String) => Using.resource(remote.evaluateTupleQuery(query))(toStats)
				withRetries(MaxAttempts)(associatedStatements(batch, kind.depth, fetchRemote))
					.map(batch -> _)
			.mapAsync(1): (batch, remoteStats) =>
				Future(blocking(applyBatch(batch, kind.depth, remoteStats)))
			.scan(SyncProgress(kind))(_ + _)

	private def rootsOf(kind: SyncKind): Source[IRI, NotUsed] =
		val classes = Source.lazySource: () =>
			val query = s"""select distinct ?cls where{
				|	values ?root { ${kind.rootClasses.map(c => s"<$c>").mkString(" ")} }
				|	?cls <${RDFS.SUBCLASSOF}>* ?root
				|}""".stripMargin
			Source(Using.resource(local.access(_.prepareTupleQuery(query).evaluate()))(
				_.map(_.getValue("cls")).collect{case cls: IRI => cls}.toIndexedSeq
			))
		classes.mapMaterializedValue(_ => NotUsed).flatMapConcat: cls =>
			Source.unfoldResource[IRI, CloseableIterator[Resource]](
				() => local.access(_.getStatements(null, RDF.TYPE, cls, false)).mapC(_.getSubject),
				iter => iter.collectFirst{case iri: IRI => iri},
				_.close()
			)

	private def applyBatch(batch: Seq[IRI], depth: Int, remoteStats: Set[Stat]): BatchResult =
		var result: BatchResult = null
		local.transact{conn =>
			val fetchLocal = (query: String) => Using.resource(conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate())(
				res => toStats(res.iterator.asScala)
			)
			val localStats = associatedStatements(batch, depth, fetchLocal)

			val toAdd = remoteStats.diff(localStats)
			val toRemove =
				if !prune then Set.empty
				else
					val reportedSubjects = remoteStats.map(st => st.graph -> st.subj)
					localStats.diff(remoteStats).filter(st => reportedSubjects.contains(st.graph -> st.subj))

			toRemove.foreach(st => conn.remove(st.subj, st.pred, st.obj, st.graph))
			toAdd.foreach(st => conn.add(st.subj, st.pred, st.obj, st.graph))

			val knownRemotely = remoteStats.map(_.subj)
			result = BatchResult(batch.size, batch.count(r => !knownRemotely.contains(r)), toAdd.size, toRemove.size)
		}.get
		result

	private def withRetries[T](attempts: Int)(fetch: => T): Future[T] =
		Future(blocking(fetch)).recoverWith:
			case err if attempts > 1 =>
				system.log.warning("Remote SPARQL fetch failed ({}), retrying", err.getMessage)
				after(RetryDelay)(withRetries(attempts - 1)(fetch))

end RemoteMetaSync

object RemoteMetaSync:
	private val MaxAttempts = 3
	private val RetryDelay = 5.seconds
	private val MaxPairsPerQuery = 500

	final case class Stat(graph: IRI, subj: IRI, pred: IRI, obj: Value)

	// links not to follow when looking for associated resources, as they point to independent resources
	private val notTraversed: Set[String] = Set(
		RDF.TYPE.stringValue,
		CpmetaPrefix + "isNextVersionOf",
		CpmetaVocab.DctermsPrefix + "hasPart"
	)

	// predicates whose values may be synthesized by the meta SPARQL endpoint (derived metadata)
	private val derivedLiteral = Seq(CpmetaPrefix + "hasBiblioInfo", CpmetaPrefix + "hasCitationString")
	private val derivedOrStored = Set(CpmetaVocab.DctermsPrefix + "license")

	/**
	 * Fetches all statements (in named graphs) about the roots, and about the IRI resources up to `depth` links
	 * away from them within the same named graph. Every resource is looked up only once, as the lookups are
	 * expensive on a meta SPARQL endpoint (derived metadata like citations get computed for every subject).
	 * Statements whose predicate may carry derived metadata are re-checked to only keep the stored ones.
	 */
	def associatedStatements(roots: Seq[IRI], depth: Int, fetch: String => IndexedSeq[Stat]): Set[Stat] =
		val rootSet = roots.toSet[Value] // already looked up in all graphs
		val queryableRoots = roots.filter(isQueryable)
		var found: Set[Stat] = if queryableRoots.isEmpty then Set.empty else fetch(rootsQuery(queryableRoots)).toSet
		var visited: Set[(IRI, IRI)] = Set.empty
		var latest = found
		for _ <- 1 to depth do
			val frontier = latest.collect{
				case Stat(g, _, p, o: IRI) if !notTraversed.contains(p.stringValue) && !rootSet.contains(o) && isQueryable(o) => o -> g
			}.diff(visited)
			visited ++= frontier
			latest = if frontier.isEmpty then Set.empty else frontier.grouped(MaxPairsPerQuery).flatMap(
				pairs => fetch(pairsQuery(pairs.toSeq))
			).toSet
			found ++= latest
		val (maybeDerived, stored) = found.partition(st => derivedOrStored.contains(st.pred.stringValue))
		val candidates = maybeDerived.filter(st => st.obj.isInstanceOf[IRI] && isQueryable(st.obj)) // licences are IRIs
		stored ++ candidates.grouped(MaxPairsPerQuery).flatMap(sts => fetch(storedOnlyQuery(sts.toSeq)))

	def rootsQuery(roots: Seq[IRI]): String =
		statementsQuery(s"values ?s { ${roots.map(r => s"<$r>").mkString(" ")} }")

	def pairsQuery(subjGraphPairs: Seq[(IRI, IRI)]): String =
		statementsQuery(s"values (?s ?g) { ${subjGraphPairs.map((s, g) => s"(<$s> <$g>)").mkString(" ")} }")

	// with the object given, the meta SPARQL endpoint does not add derived metadata to the stored statements
	private def storedOnlyQuery(candidates: Seq[Stat]): String = statementsQuery(
		s"values (?g ?s ?p ?o) { ${candidates.map(st => s"(<${st.graph}> <${st.subj}> <${st.pred}> <${st.obj}>)").mkString(" ")} }"
	)

	private def statementsQuery(values: String): String = s"""select ?g ?s ?p ?o where{
		|	$values
		|	graph ?g { ?s ?p ?o }
		|	filter(?p not in (${derivedLiteral.map(p => s"<$p>").mkString(", ")}))
		|}""".stripMargin

	// IRIs that can be written as SPARQL IRIREFs
	private def isQueryable(iri: Value): Boolean =
		!iri.stringValue.exists(c => c <= ' ' || "<>\"{}|^`\\".contains(c))

	private def toStats(bindings: Iterator[BindingSet]): IndexedSeq[Stat] = bindings
		.flatMap: bs =>
			(bs.getValue("g"), bs.getValue("s"), bs.getValue("p"), bs.getValue("o")) match
				case (g: IRI, s: IRI, p: IRI, o: Value) if !o.isInstanceOf[BNode] => Some(Stat(g, s, p, o))
				case _ => None
		.toIndexedSeq

end RemoteMetaSync
