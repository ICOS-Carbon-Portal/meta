package se.lu.nateko.cp.meta.persistence

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.openrdf.model.Resource
import org.openrdf.repository.Repository
import org.openrdf.repository.sail.SailRepository
import org.openrdf.sail.memory.MemoryStore


object RdfUpdateLogIngester{

	def ingest(updates: Iterator[RdfUpdate], context: Resource)(implicit executor: ExecutionContext): Future[Repository] = Future{

		val repo = new SailRepository(new MemoryStore)
		repo.initialize()
		val conn = repo.getConnection
		val ok = Success(())

		def commitChunk(chunk: Seq[RdfUpdate]): Try[Unit] = {
			conn.begin()
			try{
				for(update <- chunk){
					if(update.isAssertion)
						conn.add(update.statement, context)
					else
						conn.remove(update.statement, context)
				}
				conn.commit()
				ok
			}catch{
				case err: Throwable =>
					conn.rollback()
					Failure(err)
			}
		}

		val error = updates.sliding(1000, 1000)
			.map(commitChunk)
			.collectFirst{case Failure(err) => err}

		conn.close()

		error match {
			case None => repo
			case Some(err) => throw err
		}
	}

}