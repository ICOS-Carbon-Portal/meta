import scala.language.unsafeNulls
import scala.collection.mutable
import scala.util.Using

import java.nio.file.Paths

import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.repository.sail.{SailRepository, SailRepositoryConnection}
import org.eclipse.rdf4j.sail.lmdb.LmdbStore
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig
import org.slf4j.LoggerFactory

import se.lu.nateko.cp.meta.utils.rdf4j.asPlainScalaIterator
import tools.shared.config.rdfStoragePath

/*
=== Description ===
Reconciles three views of the local RDF storage (LMDB) to explain any gap
between conn.size() and the sum of per-context sizes over getContextIDs():

  1. conn.size()                              -- total statements
  2. sum of conn.size(ctx) over getContextIDs -- what the loop would upload
  3. an actual stream of every statement, bucketed by its own getContext()

If (1) != (2) but the null context is empty, the missing statements live in
contexts that getContextIDs() does not report. This streams all statements and
prints exactly which contexts hold the discrepancy.
 */

private val log = LoggerFactory.getLogger("devtools.diagnoseContexts")
private val NullKey = "<null / default context>"

@main def diagnoseContexts(): Unit = {
	withRepoConn { conn =>
		val total = conn.size()
		log.info(s"conn.size() total = $total")

		val declaredContexts = Using.resource(conn.getContextIDs()) { _.asPlainScalaIterator.toVector }
		val declaredKeys = declaredContexts.iterator.map(_.stringValue).toSet
		val declaredSum = declaredContexts.map(ctx => conn.size(ctx)).sum
		log.info(s"getContextIDs() reports ${declaredContexts.size} contexts, summed size = $declaredSum")
		log.info(s"gap (total - declaredSum) = ${total - declaredSum}")

		log.info("Streaming every statement and bucketing by its actual context...")
		val counts = mutable.LinkedHashMap.empty[String, Long]
		var streamed = 0L
		Using.resource(conn.getStatements(null, null, null, false)) { statements =>
			statements.asPlainScalaIterator.foreach { st =>
				val ctx = st.getContext
				val key = if ctx == null then NullKey else ctx.stringValue
				counts.update(key, counts.getOrElse(key, 0L) + 1L)
				streamed += 1
				if streamed % 5000000L == 0 then log.info(s"  ...streamed ${streamed / 1000000}M statements")
			}
		}
		log.info(s"Streamed $streamed statements across ${counts.size} distinct contexts")

		val undeclared = counts.filter { case (key, _) => key != NullKey && !declaredKeys.contains(key) }
		val nullCount = counts.getOrElse(NullKey, 0L)

		log.info(s"Statements in the null/default context: $nullCount")
		if undeclared.isEmpty then {
			log.info("Every non-null context is reported by getContextIDs()")
		} else {
			val undeclaredTotal = undeclared.values.sum
			log.warn(s"${undeclared.size} context(s) hold $undeclaredTotal statement(s) but are NOT reported by getContextIDs():")
			undeclared.toVector.sortBy { case (_, c) => -c }.foreach { case (key, c) =>
				log.warn(s"  $c\t$key")
			}
		}
	}
}

private def withRepo(callback: SailRepository => Any): Unit = {
	val storageDir = Paths.get(rdfStoragePath).resolve("lmdb")
	val sail = LmdbStore(storageDir.toFile, new LmdbStoreConfig())
	val repo = new SailRepository(sail)
	repo.init()
	try callback(repo) finally repo.shutDown()
}

private def withRepoConn(callback: SailRepositoryConnection => Any): Unit = {
	withRepo { repo =>
		Using.resource(repo.getConnection())(callback)
	}
}
