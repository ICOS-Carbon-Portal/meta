package se.lu.nateko.cp.meta.persistence

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.api.CloseableIterator

class InMemoryRdfLog extends RdfUpdateLog{

	private[this] val log = new ConcurrentLinkedQueue[RdfUpdate]()

	def appendAll(updates: IterableOnce[RdfUpdate]): Unit = {
		log.addAll(updates.iterator.toIndexedSeq.asJava)
	}

	def updates: CloseableIterator[RdfUpdate] = new CloseableIterator.Wrap(log.iterator.asScala, () => ())
	def timedUpdates = ???
	def close(): Unit = {}
}