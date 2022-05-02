package se.lu.nateko.cp.meta.persistence

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.repository.Repository

import scala.util.Try
import scala.util.Failure

import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import org.eclipse.rdf4j.IsolationLevels


object RdfUpdateLogIngester{

	private val chunkSize = 5000

	def ingestIntoMemory(updates: Iterator[RdfUpdate], contexts: IRI*): Repository =
		ingest(updates, Loading.emptyInMemory, false, contexts*)

	def ingest(updates: Iterator[RdfUpdate], repo: Repository, cleanFirst: Boolean, contexts: IRI*): Repository = {

		def commitChunk(chunk: Seq[RdfUpdate]): Try[Unit] = {
			val res = repo.transact(conn => {
				for(update <- chunk){
					if(update.isAssertion)
						conn.add(update.statement, contexts*)
					else
						conn.remove(update.statement, contexts*)
				}
			}, Some(IsolationLevels.NONE))
			res
		}

		if(cleanFirst) repo
			.transact(_.remove(null, null, null, contexts*), Some(IsolationLevels.NONE))
			.get //throw exception if failed to clean

		updates.sliding(chunkSize, chunkSize)
			.map(commitChunk)
			.collectFirst{case Failure(err) => err}
			match {
			case None => repo
			case Some(err) => throw err
		}
	}
}
