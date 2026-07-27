package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.doi.{Doi, DoiMeta}
import se.lu.nateko.cp.meta.core.data.References

import scala.collection.concurrent.TrieMap
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future, Promise}

class DataCiteQueueTest extends AnyFunSpec with BeforeAndAfterAll {

	private given system: ActorSystem = ActorSystem("DataCiteQueueTest")

	override def afterAll(): Unit =
		Await.result(system.terminate(), 10.seconds)

	private val factory = SimpleValueFactory.getInstance()
	private def iri(name: String): IRI = factory.createIRI("http://test.icos-cp.eu/" + name)

	/** CitationClient whose DataCite lookups complete only when the test says so. */
	private class StubCiter extends CitationClient {
		private val citPromises = TrieMap.empty[(Doi, CitationStyle), Promise[String]]
		private val doiPromises = TrieMap.empty[Doi, Promise[DoiMeta]]

		def getCitation(doi: Doi, style: CitationStyle): Future[String] =
			citPromises.getOrElseUpdate((doi, style), Promise()).future

		def getDoiMeta(doi: Doi): Future[DoiMeta] =
			doiPromises.getOrElseUpdate(doi, Promise()).future

		def complete(doi: Doi, ok: Boolean): Unit = {
			for (style <- DataCiteQueue.NeededStyles) {
				val p = citPromises.getOrElseUpdate((doi, style), Promise())
				if (ok) p.trySuccess("the citation") else p.tryFailure(new Exception("DataCite says no"))
			}
			val p = doiPromises.getOrElseUpdate(doi, Promise())
			if (ok) p.trySuccess(DoiMeta(doi)) else p.tryFailure(new Exception("DataCite says no"))
		}

		def htmlStarted(doi: Doi): Boolean = citPromises.contains(doi -> CitationStyle.HTML)
		def metadataStarted(doi: Doi): Boolean = doiPromises.contains(doi)
		def completeHtml(doi: Doi): Unit =
			citPromises.getOrElseUpdate(doi -> CitationStyle.HTML, Promise()).trySuccess("the citation")
	}

	private def makeQueue(citer: StubCiter): (DataCiteQueue, ArrayBuffer[IRI]) = {
		val written = ArrayBuffer.empty[IRI]
		val queue = DataCiteQueue(citer, completed => {
			written.synchronized{ written ++= completed.map(_.entry.subj) }
			completed.size
		})
		(queue, written)
	}

	private def entry(name: String, doi: Doi, full: Boolean = true) =
		DataCiteQueue.Entry(iri(name), doi, Option.when(full)(References.empty), None)

	private def eventually(test: => Boolean): Unit = {
		val deadline = System.nanoTime() + 3.seconds.toNanos
		while (!test && System.nanoTime() < deadline) Thread.sleep(10)
		assert(test)
	}

	describe("DataCiteQueue"){

		it("starts independent DataCite entries concurrently"){
			val citer = StubCiter()
			val (queue, written) = makeQueue(citer)
			val (doiA, doiB) = (Doi("10.18160", "A"), Doi("10.18160", "B"))

			Await.result(queue.push(entry("objA", doiA)), 3.seconds)
			Await.result(queue.push(entry("objB", doiB)), 3.seconds)
			eventually(citer.htmlStarted(doiA) && citer.htmlStarted(doiB))

			citer.complete(doiB, ok = true)
			citer.complete(doiA, ok = true)
			val total = Await.result(queue.drain(), 10.seconds)
			assert(written.toSet === Set(iri("objA"), iri("objB")))
			assert(total === 2)
			assert(queue.pending === 0)
		}

		it("skips entries whose DataCite lookups failed, keeping the rest"){
			val citer = StubCiter()
			val (queue, written) = makeQueue(citer)
			val (doiA, doiB) = (Doi("10.18160", "A"), Doi("10.18160", "B"))

			Await.result(queue.push(entry("objA", doiA)), 3.seconds)
			Await.result(queue.push(entry("objB", doiB)), 3.seconds)
			eventually(citer.htmlStarted(doiA) && citer.htmlStarted(doiB))
			citer.complete(doiA, ok = false)
			citer.complete(doiB, ok = true)

			val total = Await.result(queue.drain(), 10.seconds)
			assert(written.toSeq === Seq(iri("objB")))
			assert(total === 1)
			assert(queue.failed === 1)
		}

		it("only requests the HTML citation for citation-only entries"){
			val citer = StubCiter()
			val (queue, written) = makeQueue(citer)
			val doi = Doi("10.18160", "A")

			Await.result(queue.push(entry("objA", doi, full = false)), 3.seconds)
			eventually(citer.htmlStarted(doi))
			citer.completeHtml(doi)

			Await.result(queue.drain(), 10.seconds)
			assert(written.toSeq === Seq(iri("objA")))
			assert(!citer.metadataStarted(doi))
		}

		it("drains immediately when nothing was pushed"){
			val (queue, written) = makeQueue(StubCiter())
			assert(Await.result(queue.drain(), 10.seconds) === 0)
			assert(written.isEmpty)
		}
	}

} // end DataCiteQueueTest
