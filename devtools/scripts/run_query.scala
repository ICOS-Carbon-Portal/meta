import scala.language.unsafeNulls
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.lmdb.LmdbStore
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig
import java.nio.file.{Files, Paths}
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("devtools.runQuery")

@main def runQuery(args: String*) = {
	args.toArray.lift.apply(0) match {
		case None => {
			log.error("Expected path of query file as first argument")
		}

		case Some(queryFilePath) => {
			val queryFilePath = args(0)
			val queryContent = Files.readString(Paths.get(queryFilePath))
			log.debug(s"queryContent: $queryContent")

			withRepo { repo =>
				withConn(repo) { conn =>
					val results = conn.prepareGraphQuery(queryContent).evaluate()
					println("Results:")
					results.forEach { statement =>
						println(statement)
					}
				}
			}
		}
	}
}

def readConfig(): Config = {
	val path = new java.io.File("application.conf").getAbsoluteFile
	ConfigFactory.parseFile(path).resolve
}

def withRepo(callback: SailRepository => Any) = {
	val storagePath = readConfig().getValue("devtools.rdfStorage.path").unwrapped.toString
	log.info(s"Using rdfStorage path: $storagePath")
	val storageDir = Paths.get(storagePath).resolve("lmdb")
	val sail = LmdbStore(storageDir.toFile, new LmdbStoreConfig())
	var repo = new SailRepository(sail)

	try {
		callback(repo)
	} catch {
		case e: Exception => throw e
	} finally {
		repo.shutDown()
	}
}

def withConn(repo: SailRepository)(callback: SailRepositoryConnection => Any) = {
	val conn = repo.getConnection()
	try {
		callback(conn)
	} catch {
		case e: Exception => throw e
	} finally {
		conn.close()
	}
}
