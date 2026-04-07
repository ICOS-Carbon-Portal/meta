package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.sparql.QleverClient

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Try, Using}

object QleverUpdateLogIngester:

	val ChunkSize = 1

	def ingest(
		updates: CloseableIterator[RdfUpdate],
		client: QleverClient,
		clearFirst: Boolean,
		writeContext: IRI
	)(using ActorSystem, Materializer): Try[Unit] =

		val graphUri = writeContext.stringValue

		def sendChunk(chunk: Seq[RdfUpdate]): Unit =
			if chunk.nonEmpty then
				val groups = toConsecutiveGroups(chunk)
				for (isAssert, stmts) <- groups do
					println(s"stmts: $stmts")
					val fut = if isAssert then client.graphStoreAdd(graphUri, stmts)
										else client.graphStoreRemove(graphUri, stmts)
					Await.result(fut, 120.seconds)

		Using(updates): updates =>
			if clearFirst then
				Await.result(client.graphStoreClear(graphUri), 60.seconds)
			for chunk <- updates.sliding(ChunkSize, ChunkSize) do
				sendChunk(chunk)

	private def toConsecutiveGroups(updates: Seq[RdfUpdate]): List[(Boolean, Vector[org.eclipse.rdf4j.model.Statement])] =
		updates.foldLeft(List.empty[(Boolean, Vector[org.eclipse.rdf4j.model.Statement])]):
			case ((isAssert, stmts) :: rest, update) if isAssert == update.isAssertion =>
				(isAssert, stmts :+ update.statement) :: rest
			case (groups, update) =>
				(update.isAssertion, Vector(update.statement)) :: groups
		.reverse

end QleverUpdateLogIngester
