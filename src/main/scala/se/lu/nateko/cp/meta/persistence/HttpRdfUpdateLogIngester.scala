package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.eclipse.rdf4j.model.{IRI, Statement}
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.sparql.HttpRdfStoreClient

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*
import scala.util.{Try, Using}

object HttpRdfUpdateLogIngester:

	val ChunkSize = 1000

	def ingest(
		updates: CloseableIterator[RdfUpdate],
		client: HttpRdfStoreClient,
		clearFirst: Boolean,
		writeContext: IRI
	)(using ActorSystem, Materializer): Try[Unit] =

		val graphUri = writeContext.stringValue

		Using(updates): updates =>
			if clearFirst then
				Await.result(client.graphStoreClear(graphUri), 60.seconds)

			val repo = new SailRepository(new MemoryStore)
			repo.init()

			try
				var updatesProcessed = 0
				Using.resource(repo.getConnection): conn =>
					for update <- updates do
						if update.isAssertion then
							conn.add(update.statement)
						else
							conn.remove(update.statement)
						updatesProcessed += 1
						if updatesProcessed % 100000 == 0 then
							println(s"RDF storage $graphUri: processed $updatesProcessed updates into in-memory store")

				println(s"RDF storage $graphUri: finished processing $updatesProcessed updates, sending to RDF storage")

				var totalSent = 0
				Using.resource(repo.getConnection): conn =>
					val allStatements = conn.getStatements(null, null, null)
					try
						val batch = new java.util.ArrayList[Statement](ChunkSize)
						for stmt <- allStatements.iterator.asScala do
							batch.add(stmt)
							if batch.size >= ChunkSize then
								Await.result(client.graphStoreAdd(graphUri, batch.asScala), 120.seconds)
								totalSent += batch.size
								println(s"RDF storage $graphUri: adding ${batch.size} triples (total $totalSent)")
								batch.clear()
						if !batch.isEmpty then
							Await.result(client.graphStoreAdd(graphUri, batch.asScala), 120.seconds)
							totalSent += batch.size
							println(s"RDF storage $graphUri: adding ${batch.size} triples (total $totalSent)")
					finally
						allStatements.close()

				println(s"RDF storage $graphUri: done, sent $totalSent triples total")
			finally
				repo.shutDown()

end HttpRdfUpdateLogIngester
