package se.lu.nateko.cp.meta.persistence

import java.io.Closeable
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.api.CloseableIterator
import java.time.Instant

trait RdfUpdateLog extends Closeable{

	def appendAll(updates: IterableOnce[RdfUpdate]): Unit
	def append(updates: RdfUpdate*): Unit = appendAll(updates)

	def updates: CloseableIterator[RdfUpdate]
	def timedUpdates: CloseableIterator[(Instant, RdfUpdate)]
	//def updatesUpTo(time: Timestamp): Iterator[RdfUpdate]

}
