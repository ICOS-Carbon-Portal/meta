package se.lu.nateko.cp.meta.utils.rdf4j

import org.eclipse.rdf4j.model.{IRI, Statement}
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.sail.memory.MemoryStore

import scala.util.{Failure, Try}

object Loading {

	def fromResource(path: String, baseUri: String): Repository = fromResource(path, baseUri, RDFFormat.RDFXML)

	def fromResource(path: String, baseUri: String, format: RDFFormat): SailRepository = {
		val repo = emptyInMemory
		loadResource(repo, path, baseUri, format).get //will cast an exception if loading failed
		repo
	}

	def loadResource(repo: Repository, path: String, baseUri: String, format: RDFFormat): Try[Unit] = {
		val instStream = getClass.getResourceAsStream(path)
		val ontUri = repo.getValueFactory.createIRI(baseUri)
		repo.transact(conn => {
			conn.add(instStream, baseUri, format, ontUri)
		})
	}

	def emptyInMemory: SailRepository = {
		val repo = new SailRepository(new MemoryStore)
		repo.init()
		repo
	}

	private val chunkSize = 10000

	def fromStatements(statements: Iterator[Statement], contexts: IRI*): Repository = {

		val repo = Loading.emptyInMemory

		def commitChunk(chunk: Seq[Statement]): Try[Unit] =
			repo.transact(conn => {
				for(statement <- chunk){
					conn.add(statement, contexts*)
				}
			})

		statements.sliding(chunkSize, chunkSize)
			.map(commitChunk)
			.collectFirst{case Failure(err) => err}
			match {
			case None => repo
			case Some(err) => throw err
		}
	}
}