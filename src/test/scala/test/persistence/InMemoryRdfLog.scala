package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate

import java.util.concurrent.ConcurrentLinkedQueue
import java.time.Instant
import scala.jdk.CollectionConverters.*

class InMemoryRdfLog extends RdfUpdateLog{

	private val log = new ConcurrentLinkedQueue[(Instant, RdfUpdate)]()

	def appendAll(updates: IterableOnce[RdfUpdate]): Unit = {
		log.addAll(updates.iterator.map(Instant.now -> _).toIndexedSeq.asJava)
	}

	// Not part of RdfUpdateLog any more (that's rdfStore's read-side concern now), but
	// LoggingInstanceServerTest still asserts against it directly on this test double.
	def updates: CloseableIterator[RdfUpdate] =
		new CloseableIterator.Wrap(log.iterator.asScala.map(_._2), () => ())
	def timedUpdates: CloseableIterator[(Instant, RdfUpdate)] =
		new CloseableIterator.Wrap(log.iterator.asScala, () => ())
	def close(): Unit = {}
}
