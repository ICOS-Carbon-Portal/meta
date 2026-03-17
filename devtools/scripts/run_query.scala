import scala.language.unsafeNulls
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.lmdb.LmdbStore
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig
import java.nio.file.{Files, Paths}
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection
import org.slf4j.LoggerFactory
import org.eclipse.rdf4j.query.QueryLanguage

/*
=== Description ===
Quick script for running SPARQL queries against local RDF storage.
Currently only runs graph queries, that is queries of the form:

	construct { ?a ?b ?c }
	where { ... }

=== Example configuration snippet for application.conf ===

	devtools {
		runQuery {
			rdfStoragePath: "./someRdfStorageDir"
		}
	}

 */

private val log = LoggerFactory.getLogger("devtools.runQuery")

@main def runQuery(args: String*) = {
	args.toArray.lift.apply(0) match {
		case None => {
			log.error("Expected path of query file as first argument")
		}

		case Some(queryFilePath) => {
			val queryFilePath = args(0)
			val queryContent = Files.readString(Paths.get(queryFilePath))
			log.debug(s"queryContent: $queryContent")

			withRepoConn(conn =>
				// TODO: Only graph queries for now. Can be fleshed out later if needed.
				val results = conn.prepareGraphQuery(QueryLanguage.SPARQL, queryContent).evaluate()
				println("Results:")
				results.forEach { statement =>
					println(statement)
				}
			)
		}
	}
}

private def withRepo(callback: SailRepository => Any) = {
	val storageDir = Paths.get(devtools.config.rdfStoragePath).resolve("lmdb")
	val sail = LmdbStore(storageDir.toFile, new LmdbStoreConfig())
	var repo = new SailRepository(sail)

	try {
		callback(repo)
	} finally {
		repo.shutDown()
	}
}

private def withRepoConn(callback: SailRepositoryConnection => Any) = {
	withRepo(repo =>
		val conn = repo.getConnection()
		try {
			callback(conn)
		} finally {
			conn.close()
		}
	)
}
