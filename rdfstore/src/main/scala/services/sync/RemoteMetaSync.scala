package se.lu.nateko.cp.meta.services.sync

import scala.language.unsafeNulls

import akka.NotUsed
import akka.actor.ActorSystem
import akka.pattern.after
import akka.stream.scaladsl.Source
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS}
import org.eclipse.rdf4j.model.{BNode, IRI, Resource, Value}
import org.eclipse.rdf4j.query.{BindingSet, QueryLanguage}
import org.eclipse.rdf4j.repository.{Repository, RepositoryConnection}
import se.lu.nateko.cp.meta.OntoConstants.CpmetaPrefix
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlRunner}
import se.lu.nateko.cp.meta.core.data.{EnvriConfigs, EnvriResolver}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.jdk.CollectionConverters.*
import scala.util.Using

/**
 * Kinds of resources to synchronize. Instances of `rootClasses` (or of their subclasses, according to the
 * ontologies in the local store) are the roots; `depth` is how many links away from a root (within the same
 * named graph) the associated resources may be. Kinds that are not `batched` are few enough for all their
 * resources to be fetched with a single SPARQL query.
 */
enum SyncKind(val rootClasses: Seq[String], val depth: Int, val batched: Boolean):
	// only the predicates indexed by the SPARQL magic index (see RemoteMetaSync.dataObjectStatements), about the
	// objects and their acquisitions, submissions and next-version collections (one link away)
	case DataObjects extends SyncKind(SyncKind.cpmeta("StaticObject", "DataObject", "DocumentObject"), 1, true)
	// spatial coverage, contributor lists
	case Collections extends SyncKind(SyncKind.cpmeta("PlainCollection", "Collection"), 1, true)
	// memberships
	case People extends SyncKind(SyncKind.cpmeta("Person"), 1, false)
	// fundings, webpage elements and their links
	case Stations extends SyncKind(SyncKind.cpmeta("Station") :+ (CpmetaVocab.SitesPrefix + "Station"), 2, false)
	// dataset specs, their columns/variables and the value types of those
	case ObjectSpecs extends SyncKind(SyncKind.cpmeta("DataObjectSpec", "SimpleObjectSpec"), 3, false)

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
 * Data objects and collections are fetched in batches; every other kind is fetched with one query.
 * Statements are only ever added, unless `prune` is set, in which case local statements about a subject are
 * removed if the remote endpoint reported that subject (in the same graph), but not the statement.
 * Blank nodes are ignored, as they cannot be matched between the repositories.
 * The updates bypass the RDF log; they are lost if the store is later restored from the log.
 */
final class RemoteMetaSync(
	local: Repository,
	remote: SparqlRunner,
	prune: Boolean,
	throttle: QueryThrottle,
	batchSize: Int = 50,
	dataObjectBatchSize: Int = 500
)(using system: ActorSystem, envriConfigs: EnvriConfigs):
	import RemoteMetaSync.*
	private given ExecutionContext = system.dispatcher
	private val vocab = CpVocab(local.getValueFactory)

	private val log = system.log

	private val fetchRemote: Fetch = query => throttle:
		val start = System.nanoTime
		val rows = Using.resource(remote.evaluateTupleQuery(query))(_.toIndexedSeq)
		val millis = (System.nanoTime - start) / 1000000
		if millis > SlowQueryMillis then
			log.warning("Slow remote SPARQL query: {} ms for {} rows, {} characters of query", millis, rows.size, query.length)
		else log.debug("Remote SPARQL query: {} ms for {} rows", millis, rows.size)
		rows

	def run(kinds: Seq[SyncKind]): Source[SyncProgress, NotUsed] =
		Source(kinds.distinct).flatMapConcat: kind =>
			Source.lazySource: () =>
				log.info("Synchronizing {}", kind)
				if kind.batched then syncInBatches(kind) else syncAtOnce(kind)
			.mapMaterializedValue(_ => NotUsed)

	private def syncInBatches(kind: SyncKind): Source[SyncProgress, NotUsed] =
		rootsOf(kind)
			.grouped(if kind == SyncKind.DataObjects then dataObjectBatchSize else batchSize)
			.mapAsync(1): batch =>
				withRetries(RetryDelays)(batchStatements(kind, batch, fetchRemote))
					.map(batch -> _)
			.mapAsync(1): (batch, remoteStats) =>
				Future(blocking(applyBatch(kind, batch, remoteStats)))
			.scan(SyncProgress(kind))(_ + _)

	private def syncAtOnce(kind: SyncKind): Source[SyncProgress, NotUsed] =
		Source.lazyFuture: () =>
			for
				query <- Future(blocking(entitiesQuery(classesOf(kind), kind.depth)))
				remoteQuads <- withRetries(RetryDelays)(fetchQuads(query, fetchRemote))
				result <- Future(blocking(applyAtOnce(kind, query, remoteQuads)))
			yield SyncProgress(kind) + result

	private def classesOf(kind: SyncKind): IndexedSeq[IRI] =
		val query = s"""select distinct ?cls where{
			|	values ?root { ${kind.rootClasses.map(c => s"<$c>").mkString(" ")} }
			|	?cls <${RDFS.SUBCLASSOF}>* ?root
			|}""".stripMargin
		Using.resource(local.access(_.prepareTupleQuery(query).evaluate()))(
			_.map(_.getValue("cls")).collect{case cls: IRI if isQueryable(cls) => cls}.toIndexedSeq
		)

	private def rootsOf(kind: SyncKind): Source[IRI, NotUsed] =
		Source.lazySource(() => Source(classesOf(kind))).mapMaterializedValue(_ => NotUsed).flatMapConcat: cls =>
			Source.unfoldResource[IRI, CloseableIterator[Resource]](
				() => local.access(_.getStatements(null, RDF.TYPE, cls, false)).mapC(_.getSubject),
				iter => iter.collectFirst{case iri: IRI => iri},
				_.close()
			)

	private def applyBatch(kind: SyncKind, batch: Seq[IRI], remoteStats: Set[Stat]): BatchResult =
		val (added, removed) = local.transactAndGet: conn =>
			applyDiff(conn, batchStatements(kind, batch, fetchLocal(conn)), remoteStats)
		logChanges(kind, added, removed)
		val knownRemotely = remoteStats.map(_.subj)
		val missing = batch.filterNot(knownRemotely.contains)
		logMissing(kind, missing)
		BatchResult(batch.size, missing.size, added.size, removed.size)

	private def applyAtOnce(kind: SyncKind, query: String, allRemoteQuads: Set[Quad]): BatchResult =
		val (knownRoots, remoteRoots, added, removed) = local.transactAndGet: conn =>
			val localQuads = fetchQuads(query, fetchLocal(conn))
			val knownRoots = localQuads.map(_.root)
			// only the locally known resources are synchronized
			val remoteQuads = allRemoteQuads.filter(q => knownRoots.contains(q.root))
			val (added, removed) = applyDiff(conn, localQuads.map(_.stat), remoteQuads.map(_.stat))
			(knownRoots, remoteQuads.map(_.root), added, removed)
		logChanges(kind, added, removed)
		val missing = knownRoots.diff(remoteRoots)
		logMissing(kind, missing)
		BatchResult(knownRoots.size, missing.size, added.size, removed.size)

	private def logMissing(kind: SyncKind, missing: Iterable[IRI]): Unit =
		if missing.nonEmpty then log.info("{}: {} resources not found remotely, e.g. <{}>", kind, missing.size, missing.head)

	private def logChanges(kind: SyncKind, added: Set[Stat], removed: Set[Stat]): Unit =
		if added.nonEmpty || removed.nonEmpty then
			val subjects = (added ++ removed).map(_.subj).size
			log.info("{}: added {} and removed {} statements about {} resources", kind, added.size, removed.size, subjects)
			if log.isDebugEnabled then
				added.foreach(st => log.debug("{}: added {}", kind, st))
				removed.foreach(st => log.debug("{}: removed {}", kind, st))

	private def applyDiff(conn: RepositoryConnection, localStats: Set[Stat], remoteStats: Set[Stat]): (Set[Stat], Set[Stat]) =
		val toAdd = remoteStats.diff(localStats)
		val toRemove =
			if !prune then Set.empty
			else
				val reportedSubjects = remoteStats.map(st => st.graph -> st.subj)
				localStats.diff(remoteStats).filter(st => reportedSubjects.contains(st.graph -> st.subj))

		toRemove.foreach(st => conn.remove(st.subj, st.pred, st.obj, st.graph))
		toAdd.foreach(st => conn.add(st.subj, st.pred, st.obj, st.graph))
		toAdd -> toRemove

	private def batchStatements(kind: SyncKind, batch: Seq[IRI], fetch: Fetch): Set[Stat] =
		if kind == SyncKind.DataObjects then dataObjectStatements(batch, vocab, fetch)
		else associatedStatements(batch, kind.depth, fetch)

	private def withRetries[T](delays: Seq[FiniteDuration])(fetch: => T): Future[T] =
		Future(blocking(fetch)).recoverWith:
			case err if delays.nonEmpty =>
				log.warning("Remote SPARQL fetch failed ({}), retrying in {}", err.getMessage, delays.head)
				after(delays.head)(withRetries(delays.tail)(fetch))

end RemoteMetaSync

object RemoteMetaSync:
	// long enough for a remote SPARQL endpoint to lift a temporary ban for overuse
	private val RetryDelays = Seq(1.minute, 15.minutes)
	// meta SPARQL endpoints stop queries after 9 seconds by default
	private val SlowQueryMillis = 5000
	private val MaxPairsPerQuery = 500

	type Fetch = String => IndexedSeq[BindingSet]

	final case class Stat(graph: IRI, subj: IRI, pred: IRI, obj: Value):
		override def toString =
			val objStr = obj match
				case iri: IRI => s"<$iri>"
				case other => other.toString
			s"<$subj> <$pred> $objStr (in <$graph>)"
	final case class Quad(root: IRI, stat: Stat)

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
	 * The predicates indexed by the SPARQL magic index (see IndexData.processUpdate) for data objects and their
	 * acquisitions, submissions and next-version collections; the only ones synchronized for data objects for now.
	 * None of them carries derived metadata, so a meta SPARQL endpoint does not compute any when they are given.
	 */
	val dataObjectPredicates: Seq[String] =
		import CpmetaVocab.{DctermsPrefix, ProvPrefix}
		Seq(RDF.TYPE.stringValue, DctermsPrefix + "hasPart") ++
		Seq("wasAssociatedWith", "startedAtTime", "endedAtTime").map(ProvPrefix + _) ++
		Seq(
			"hasObjectSpec", "hasName", "wasPerformedAt", "hasStartTime", "hasEndTime", "isNextVersionOf",
			"hasSizeInBytes", "hasSamplingHeight", "hasActualColumnNames", "hasActualVariable", "hasKeywords"
		).map(CpmetaPrefix + _)

	/**
	 * Fetches the statements with `dataObjectPredicates` about the data objects, their acquisitions and submissions,
	 * and the next-version collections of them, with a single query. The latter are identified by the hash of the
	 * object (as by the SPARQL magic index), not looked up by links, as such lookups are slow on meta SPARQL
	 * endpoints (they get rewritten into data-object index scans).
	 */
	def dataObjectStatements(objects: Seq[IRI], vocab: CpVocab, fetch: Fetch)(using EnvriConfigs): Set[Stat] =
		val subjects = objects.filter(isQueryable).flatMap(dataObjectSubjects(_, vocab))
		if subjects.isEmpty then Set.empty else toStats(fetch(dataObjectsQuery(subjects))).toSet

	private def dataObjectSubjects(obj: IRI, vocab: CpVocab)(using EnvriConfigs): Seq[IRI] = obj match
		case CpVocab.DataObject(hash, _) =>
			EnvriResolver.infer(URI(obj.stringValue)).fold(Seq(obj)): envri =>
				given Envri = envri
				Seq(obj, vocab.getAcquisition(hash), vocab.getSubmission(hash), vocab.getNextVersionColl(hash))
		case _ => Seq(obj)

	def dataObjectsQuery(subjects: Seq[IRI]): String =
		s"""select ?g ?s ?p ?o where{
			|	values ?s { ${subjects.map(s => s"<$s>").mkString(" ")} }
			|	values ?p { ${dataObjectPredicates.map(p => s"<$p>").mkString(" ")} }
			|	graph ?g { ?s ?p ?o }
			|}""".stripMargin

	private def fetchLocal(conn: RepositoryConnection): Fetch = query =>
		Using.resource(conn.prepareTupleQuery(QueryLanguage.SPARQL, query).evaluate())(_.iterator.asScala.toIndexedSeq)

	extension (repo: Repository)
		private def transactAndGet[T](action: RepositoryConnection => T): T =
			var result: Option[T] = None
			repo.transact(conn => result = Some(action(conn))).get
			result.get

	/**
	 * Fetches all statements (in named graphs) about the roots, and about the IRI resources up to `depth` links
	 * away from them within the same named graph. Every resource is looked up only once, as the lookups are
	 * expensive on a meta SPARQL endpoint (derived metadata like citations get computed for every subject).
	 */
	def associatedStatements(roots: Seq[IRI], depth: Int, fetch: Fetch): Set[Stat] =
		val rootSet = roots.toSet[Value] // already looked up in all graphs
		val queryableRoots = roots.filter(isQueryable)
		var found: Set[Stat] = if queryableRoots.isEmpty then Set.empty else toStats(fetch(rootsQuery(queryableRoots))).toSet
		var visited: Set[(IRI, IRI)] = Set.empty
		var latest = found
		for _ <- 1 to depth do
			val frontier = latest.collect{
				case Stat(g, _, p, o: IRI) if !notTraversed.contains(p.stringValue) && !rootSet.contains(o) && isQueryable(o) => o -> g
			}.diff(visited)
			visited ++= frontier
			latest = if frontier.isEmpty then Set.empty else frontier.grouped(MaxPairsPerQuery).flatMap(
				pairs => toStats(fetch(pairsQuery(pairs.toSeq)))
			).toSet
			found ++= latest
		onlyStored(found, fetch)

	/** Fetches the statements of `entitiesQuery`, attributed to their roots */
	def fetchQuads(query: String, fetch: Fetch): Set[Quad] =
		val quads = fetch(query).flatMap: bs =>
			(bs.getValue("root"), statOf(bs)) match
				case (root: IRI, Some(stat)) => Some(Quad(root, stat))
				case _ => None
		.toSet
		val stored = onlyStored(quads.map(_.stat), fetch)
		quads.filter(q => stored.contains(q.stat))

	/**
	 * Statements whose predicate may carry derived metadata are re-checked to only keep the stored ones.
	 * With the object given, the meta SPARQL endpoint does not add derived metadata to the stored statements.
	 */
	private def onlyStored(stats: Set[Stat], fetch: Fetch): Set[Stat] =
		val (maybeDerived, stored) = stats.partition(st => derivedOrStored.contains(st.pred.stringValue))
		val candidates = maybeDerived.filter(st => st.obj.isInstanceOf[IRI] && isQueryable(st.obj)) // licences are IRIs
		stored ++ candidates.grouped(MaxPairsPerQuery).flatMap: sts =>
			toStats(fetch(statementsQuery(
				s"values (?g ?s ?p ?o) { ${sts.map(st => s"(<${st.graph}> <${st.subj}> <${st.pred}> <${st.obj}>)").mkString(" ")} }"
			)))

	def rootsQuery(roots: Seq[IRI]): String =
		statementsQuery(s"values ?s { ${roots.map(r => s"<$r>").mkString(" ")} }")

	def pairsQuery(subjGraphPairs: Seq[(IRI, IRI)]): String =
		statementsQuery(s"values (?s ?g) { ${subjGraphPairs.map((s, g) => s"(<$s> <$g>)").mkString(" ")} }")

	private def statementsQuery(values: String): String = s"""select ?g ?s ?p ?o where{
		|	$values
		|	graph ?g { ?s ?p ?o }
		|	$derivedLiteralFilter
		|}""".stripMargin

	/**
	 * A single query for all statements associated with all instances of the given classes: the statements about
	 * the instances themselves, and about the IRI resources up to `depth` links away from them within the same
	 * named graph.
	 */
	def entitiesQuery(classes: Seq[IRI], depth: Int): String =
		val notTraversedList = notTraversed.map(p => s"<$p>").mkString(", ")
		val classValues = s"values ?cls { ${classes.map(c => s"<$c>").mkString(" ")} }"
		val branches = (0 to depth).map: hops =>
			val links = (1 to hops).map: i =>
				val from = if i == 1 then "?root" else s"?n${i - 1}"
				val to = if i == hops then "?s" else s"?n$i"
				s"$from ?l$i $to . filter(isIRI($to) && ?l$i not in ($notTraversedList))"
			val inGraph = if hops == 0 then "?root ?p ?o" else (links :+ "?s ?p ?o").mkString("\n\t\t\t\t\t")
			val bindSubject = if hops == 0 then "bind(?root as ?s)" else ""
			// a separate subquery for every depth, as rdf4j optimizes the union of plain groups poorly
			s"""{
				|		select ?root ?g ?s ?p ?o where{
				|			$classValues
				|			?root a ?cls .
				|			graph ?g {
				|				$inGraph
				|			}
				|			$bindSubject
				|		}
				|	}""".stripMargin

		s"""select distinct ?root ?g ?s ?p ?o where{
			|	${branches.mkString("\n\tunion\n\t")}
			|	$derivedLiteralFilter
			|}""".stripMargin

	private def derivedLiteralFilter = s"filter(?p not in (${derivedLiteral.map(p => s"<$p>").mkString(", ")}))"

	// IRIs that can be written as SPARQL IRIREFs
	private def isQueryable(iri: Value): Boolean =
		!iri.stringValue.exists(c => c <= ' ' || "<>\"{}|^`\\".contains(c))

	private def statOf(bs: BindingSet): Option[Stat] =
		(bs.getValue("g"), bs.getValue("s"), bs.getValue("p"), bs.getValue("o")) match
			case (g: IRI, s: IRI, p: IRI, o: Value) if !o.isInstanceOf[BNode] => Some(Stat(g, s, p, o))
			case _ => None

	private def toStats(bindings: IndexedSeq[BindingSet]): IndexedSeq[Stat] = bindings.flatMap(statOf)

end RemoteMetaSync
