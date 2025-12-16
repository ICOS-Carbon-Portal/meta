import scala.language.unsafeNulls
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.lmdb.LmdbStore
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig
import java.nio.file.{Files, Paths}

@main def runQuery(args: String*) = {
	val fileName = args.head
	val queryContent = Files.readString(Paths.get(fileName))
	println(s"content: $queryContent")

	val storageDir = Paths.get("/home/ggvgc/bulk/rdfStorage").resolve("lmdb")
	val sail = LmdbStore(storageDir.toFile, new LmdbStoreConfig())
	var repo = new SailRepository(sail)
	val conn = repo.getConnection()

	try {
		val res = Rdf4jSparqlRunner(repo).evaluateGraphQuery(SparqlQuery(queryContent)).toIndexedSeq
		for (entry <- res) {
			println(entry)
		}
	} catch {
		case e: Exception => throw e
	} finally {
		conn.close()
		repo.shutDown()
	}

}
