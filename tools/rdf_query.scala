import scala.language.unsafeNulls
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.RepositoryConnection
import java.nio.file.{Files, Paths}
import org.slf4j.LoggerFactory
import org.eclipse.rdf4j.query.QueryLanguage
import se.lu.nateko.cp.meta.ConfigLoader
import se.lu.nateko.cp.meta.services.sparql.RemoteRepository

/*
=== Description ===
Quick script for running SPARQL queries against the configured remote
SPARQL endpoint (cpmeta.rdfStorage in application.conf).
Currently only runs graph queries, that is queries of the form:

	construct { ?a ?b ?c }
	where { ... }

 */

private val log = LoggerFactory.getLogger("devtools.rdfQuery")

@main def rdfQuery(args: String*) = {
	args.toArray.lift.apply(0) match {
		case None => {
			log.error("Expected path of query file as first argument")
		}

		case Some(queryFilePath) => {
			val queryContent = Files.readString(Paths.get(queryFilePath))
			log.debug(s"queryContent: $queryContent")

			withRepoConn(conn =>
				val results = conn.prepareGraphQuery(QueryLanguage.SPARQL, queryContent).evaluate()
				println("Results:")
				results.forEach { statement =>
					println(statement)
				}
			)
		}
	}
}

private def withRepo(callback: Repository => Any) = {
	val repo = RemoteRepository.apply(ConfigLoader.default.rdfStorage)
	try callback(repo) finally repo.shutDown()
}

private def withRepoConn(callback: RepositoryConnection => Any) = {
	withRepo(repo =>
		val conn = repo.getConnection()
		try callback(conn) finally conn.close()
	)
}
