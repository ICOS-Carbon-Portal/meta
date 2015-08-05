package se.lu.nateko.cp.meta.persistence

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Try
import org.openrdf.model.URI
import org.openrdf.repository.Repository
import org.openrdf.repository.sail.SailRepository
import org.openrdf.sail.memory.MemoryStore
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate


object RdfUpdateLogIngester{

	private val chunkSize = 5000

	def ingest(updates: Iterator[RdfUpdate], context: URI)(implicit executor: ExecutionContext): Future[Repository] =
		ingest(updates, Loading.empty, context)

	def ingest(updates: Iterator[RdfUpdate], repo: Repository, context: URI)(implicit executor: ExecutionContext): Future[Repository] = Future{

		def commitChunk(chunk: Seq[RdfUpdate]): Try[Unit] =
			repo.transact(conn => {
				for(update <- chunk){
					if(update.isAssertion)
						conn.add(update.statement, context)
					else
						conn.remove(update.statement, context)
				}
			})

		updates.sliding(chunkSize, chunkSize)
			.map(commitChunk)
			.collectFirst{case Failure(err) => err}
			match {
			case None => repo
			case Some(err) => throw err
		}
	}

}