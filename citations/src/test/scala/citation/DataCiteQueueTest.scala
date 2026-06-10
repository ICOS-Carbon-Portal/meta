package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.doi.{Doi, DoiMeta}

import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future, Promise}

class DataCiteQueueTest extends AnyFunSpec with BeforeAndAfterAll:

	private given system: ActorSystem = ActorSystem("DataCiteQueueTest")

	override def afterAll(): Unit =
		Await.result(system.terminate(), 10.seconds)

	private val factory = SimpleValueFactory.getInstance()
	private def iri(name: String): IRI = factory.createIRI("http://test.icos-cp.eu/" + name)

	/** CitationClient whose DataCite lookups complete only when the test says so. */
	private class StubCiter extends CitationClient:
		private val citPromises = TrieMap.empty[(Doi, CitationStyle), Promise[String]]
		private val doiPromises = TrieMap.empty[Doi, Promise[DoiMeta]]

		def getCitation(doi: Doi, style: CitationStyle): Future[String] =
			citPromises.getOrElseUpdate((doi, style), Promise()).future

		def getDoiMeta(doi: Doi): Future[DoiMeta] =
			doiPromises.getOrElseUpdate(doi, Promise()).future

		def complete(doi: Doi, ok: Boolean): Unit =
			for style <- DataCiteQueue.NeededStyles do
				val p = citPromises.getOrElseUpdate((doi, style), Promise())
				if ok then p.trySuccess("the citation") else p.tryFailure(new Exception("DataCite says no"))
			val p = doiPromises.getOrElseUpdate(doi, Promise())
			if ok then p.trySuccess(DoiMeta(doi)) else p.tryFailure(new Exception("DataCite says no"))

	private def makeQueue(citer: StubCiter): (DataCiteQueue, ArrayBuffer[IRI]) =
		val written = ArrayBuffer.empty[IRI]
		val queue = DataCiteQueue(citer, subjs => { written.synchronized{ written ++= subjs }; subjs.size })
		(queue, written)

	describe("DataCiteQueue"){

		it("materializes entries in push order even when DataCite resolves out of order"){
			val citer = StubCiter()
			val (queue, written) = makeQueue(citer)
			val (doiA, doiB) = (Doi("10.18160", "A"), Doi("10.18160", "B"))

			queue.push(iri("objA"), doiA)
			queue.push(iri("objB"), doiB)

			citer.complete(doiB, ok = true) // B's lookups finish first...
			Thread.sleep(200)
			assert(written.isEmpty) // ...but A at the queue head is not done, so nothing is written yet

			citer.complete(doiA, ok = true)
			val total = Await.result(queue.drain(), 10.seconds)
			assert(written.toSeq === Seq(iri("objA"), iri("objB")))
			assert(total === 2)
			assert(queue.pending === 0)
		}

		it("skips entries whose DataCite lookups failed, keeping the rest"){
			val citer = StubCiter()
			val (queue, written) = makeQueue(citer)
			val (doiA, doiB) = (Doi("10.18160", "A"), Doi("10.18160", "B"))

			queue.push(iri("objA"), doiA)
			queue.push(iri("objB"), doiB)
			citer.complete(doiA, ok = false)
			citer.complete(doiB, ok = true)

			val total = Await.result(queue.drain(), 10.seconds)
			assert(written.toSeq === Seq(iri("objB")))
			assert(total === 1)
			assert(queue.failed === 1)
		}

		it("drains immediately when nothing was pushed"){
			val (queue, written) = makeQueue(StubCiter())
			assert(Await.result(queue.drain(), 10.seconds) === 0)
			assert(written.isEmpty)
		}
	}

end DataCiteQueueTest
