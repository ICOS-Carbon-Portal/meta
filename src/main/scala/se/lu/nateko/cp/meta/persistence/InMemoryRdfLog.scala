package se.lu.nateko.cp.meta.persistence

import java.util.concurrent.ConcurrentLinkedQueue
import scala.jdk.CollectionConverters._
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate

class InMemoryRdfLog extends RdfUpdateLog{

	private[this] val log = new ConcurrentLinkedQueue[RdfUpdate]()

	def appendAll(updates: IterableOnce[RdfUpdate]): Unit = {
		log.addAll(updates.iterator.toIndexedSeq.asJava)
	}

	def updates: Iterator[RdfUpdate] = log.iterator.asScala
	//def updatesUpTo(time: Timestamp): Iterator[RdfUpdate] = ???
	def close(): Unit = {}
}