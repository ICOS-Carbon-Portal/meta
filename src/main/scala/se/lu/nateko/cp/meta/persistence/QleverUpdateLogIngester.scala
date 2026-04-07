package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.eclipse.rdf4j.model.{IRI, Statement}
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.sparql.QleverClient

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Try, Using}

object QleverUpdateLogIngester:

	val ChunkSize = 1000

	def ingest(
		updates: CloseableIterator[RdfUpdate],
		client: QleverClient,
		clearFirst: Boolean,
		writeContext: IRI
	)(using ActorSystem, Materializer): Try[Unit] =

		val graphUri = writeContext.stringValue

		var totalSent = 0

		def sendBatch(isAssertion: Boolean, stmts: Iterable[Statement]): Unit =
			val kind = if isAssertion then "adding" else "removing"
			val size = stmts.size
			val fut = if isAssertion then client.graphStoreAdd(graphUri, stmts)
						else client.graphStoreRemove(graphUri, stmts)
			Await.result(fut, 120.seconds)
			totalSent += size
			println(s"Qlever $graphUri: $kind $size triples (total $totalSent)")

		Using(updates): updates =>
			if clearFirst then
				Await.result(client.graphStoreClear(graphUri), 60.seconds)

			val buffer = ArrayBuffer.empty[Statement]
			var currentIsAssertion = true
			var bufferNonEmpty = false

			for update <- updates do
				if bufferNonEmpty && (update.isAssertion != currentIsAssertion || buffer.size >= ChunkSize) then
					sendBatch(currentIsAssertion, buffer)
					buffer.clear()
					bufferNonEmpty = false

				if !bufferNonEmpty then
					currentIsAssertion = update.isAssertion
					bufferNonEmpty = true

				buffer += update.statement

			if bufferNonEmpty then
				sendBatch(currentIsAssertion, buffer)

end QleverUpdateLogIngester
