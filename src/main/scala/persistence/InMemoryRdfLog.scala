package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters.*

class InMemoryRdfLog extends RdfUpdateLog{

	private val log = new ConcurrentLinkedQueue[RdfUpdate]()

	def appendAll(updates: IterableOnce[RdfUpdate]): Unit = {
		log.addAll(updates.iterator.toIndexedSeq.asJava)
	}

	def updates: CloseableIterator[RdfUpdate] = new CloseableIterator.Wrap(log.iterator.asScala, () => ())
	def timedUpdates = ???
	def close(): Unit = {}
}
