import scala.language.unsafeNulls
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.lmdb.LmdbStore
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig
import java.nio.file.Paths
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection
import org.slf4j.LoggerFactory
import org.eclipse.rdf4j.query.QueryLanguage

private val log = LoggerFactory.getLogger("devtools.deleteLevel0")

@main def deleteLevel0(args: String*) = {
	val deleteObjectsQuery = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		DELETE {
			?obj ?pred ?target
		} where{
			?obj cpmeta:hasObjectSpec ?spec .
			?spec cpmeta:hasDataLevel "0"^^xsd:integer .
			?obj ?pred ?target
		}
	"""

	val deleteSpecsQuery = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		DELETE {
			?subj ?pred ?spec
		} where{
			?spec cpmeta:hasDataLevel "0"^^xsd:integer .
			?subj ?pred ?spec
		}
	"""

	val specQuery = """
		prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		select distinct ?spec
			where{
			 ?obj cpmeta:hasObjectSpec ?spec .
			 ?spec cpmeta:hasDataLevel "0"^^xsd:integer
			}
	"""

	withRepo { repo =>
		withConn(repo) { conn =>
			println("Deleting objects!")
			conn.prepareUpdate(QueryLanguage.SPARQL, deleteObjectsQuery).execute()
			println("Done!")

			println("Deleting specs!")
			conn.prepareUpdate(QueryLanguage.SPARQL, deleteSpecsQuery).execute()
			println("Done!")

			println("Remaining level 0 specs (should be none):")
			conn.prepareTupleQuery(specQuery).evaluate().forEach { statement =>
				println(statement)
			}
			println("Done!")
		}
	}
}

private def readConfig(): Config = {
	val path = new java.io.File("application.conf").getAbsoluteFile
	ConfigFactory.parseFile(path).resolve
}

private def withRepo(callback: SailRepository => Any): Unit = {
	val storagePath = readConfig().getValue("devtools.rdfStorage.path").unwrapped.toString
	log.info(s"Using rdfStorage path: $storagePath")
	val storageDir = Paths.get(storagePath).resolve("lmdb")
	val sail = LmdbStore(storageDir.toFile, new LmdbStoreConfig())
	var repo = new SailRepository(sail)

	try {
		callback(repo)
	} finally {
		repo.shutDown()
	}
}

private def withConn[T](repo: SailRepository)(callback: SailRepositoryConnection => Any): Unit = {
	val conn = repo.getConnection()
	try {
		callback(conn)
	} finally {
		conn.close()
	}
}
