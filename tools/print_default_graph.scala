import scala.language.unsafeNulls
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
Prints every statement stored WITHOUT a named graph (the default/null context)
in the local RDF storage (LMDB).

These are exactly the statements that getContextIDs() does NOT enumerate, and
that populateVirtuoso re-homes into the 'fromdefault' named graph. Use this to
inspect what actually lives in the default graph before/after ingestion.
 */

private val log = LoggerFactory.getLogger("devtools.printDefaultGraph")

@main def printDefaultGraph(): Unit = {
	// A single null in the contexts array selects the default (null) context only,
	// as opposed to an empty array which would match every context.
	val nullContext = Array[Resource](null)

	withRepoConn { conn =>
		val total = conn.size(nullContext*)
		log.info(s"Default (null) context holds $total statement(s)")

		var printed = 0L
		Using.resource(conn.getStatements(null, null, null, false, nullContext*)) { statements =>
			statements.asPlainScalaIterator.foreach { st =>
				println(st)
				printed += 1
			}
		}
		log.info(s"Printed $printed statement(s) from the default (null) context")
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
