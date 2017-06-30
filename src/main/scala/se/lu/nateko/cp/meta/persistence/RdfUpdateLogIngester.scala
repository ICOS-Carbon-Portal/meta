package se.lu.nateko.cp.meta.persistence

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.repository.Repository

import scala.util.Try
import scala.util.Failure

import se.lu.nateko.cp.meta.utils.rdf4j._
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate


object RdfUpdateLogIngester{

	private val chunkSize = 5000

	def ingest(updates: Iterator[RdfUpdate], contexts: IRI*): Repository =
		ingest(updates, Loading.empty, contexts: _*)

	def ingest(updates: Iterator[RdfUpdate], repo: Repository, contexts: IRI*): Repository = {

		def commitChunk(chunk: Seq[RdfUpdate]): Try[Unit] =
			repo.transact(conn => {
				for(update <- chunk){
					if(update.isAssertion)
						conn.add(update.statement, contexts: _*)
					else
						conn.remove(update.statement, contexts: _*)
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