package se.lu.nateko.cp.meta.utils.sesame

import org.openrdf.repository.Repository
import org.openrdf.repository.sail.SailRepository
import org.openrdf.sail.memory.MemoryStore
import org.openrdf.rio.RDFFormat
import org.openrdf.model.Statement
import org.openrdf.model.URI
import scala.util.Failure
import scala.util.Try

object Loading {

	def fromResource(path: String, baseUri: String): Repository = {
		val instStream = getClass.getResourceAsStream(path)
		val repo = empty
		val ontUri = repo.getValueFactory.createURI(baseUri)
		repo.transact(conn => {
			conn.add(instStream, baseUri, RDFFormat.RDFXML, ontUri)
		})
		repo
	}

	def empty: Repository = {
		val repo = new SailRepository(new MemoryStore)
		repo.initialize()
		repo
	}

	private val chunkSize = 1000

	def fromStatements(statements: Iterator[Statement], context: URI): Repository = {

		val repo = Loading.empty

		def commitChunk(chunk: Seq[Statement]): Try[Unit] =
			repo.transact(conn => {
				for(statement <- chunk){
					conn.add(statement, context)
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