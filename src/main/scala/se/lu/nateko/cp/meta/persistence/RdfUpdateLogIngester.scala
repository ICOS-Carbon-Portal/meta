package se.lu.nateko.cp.meta.persistence

import org.eclipse.rdf4j.common.transaction.IsolationLevels
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.utils.rdf4j.*

import scala.util.{Failure, Try, Using}


object RdfUpdateLogIngester:

	val ChunkSize = 100000

	def ingestIntoMemory(updates: CloseableIterator[RdfUpdate], contexts: IRI*): Repository =
		val repo = Loading.emptyInMemory
		ingest(updates, repo, false, contexts*)
		repo

	def ingest(updates: CloseableIterator[RdfUpdate], repo: Repository, cleanFirst: Boolean, contexts: IRI*): Try[Unit] =

		def commitChunk(chunk: Seq[RdfUpdate]): Try[Unit] = repo.transact(
			conn =>
				for update <- chunk do
					if(update.isAssertion)
						conn.add(update.statement, contexts*)
					else
						conn.remove(update.statement, contexts*)
		)

		Using(updates): updates =>
			if cleanFirst then clean(repo, contexts*).get //throw exception if failed to clean
			updates.sliding(ChunkSize, ChunkSize).foreach(commitChunk(_).get)

	end ingest

	def clean(repo: Repository, contexts: IRI*): Try[Unit] = repo
		.transact(_.remove(null, null, null, contexts*))

end RdfUpdateLogIngester
