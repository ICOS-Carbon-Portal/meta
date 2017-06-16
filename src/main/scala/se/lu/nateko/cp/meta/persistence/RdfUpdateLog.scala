package se.lu.nateko.cp.meta.persistence

import org.eclipse.rdf4j.model.Statement
import java.io.Closeable
import java.sql.Timestamp
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate

trait RdfUpdateLog extends Closeable{

	def appendAll(updates: TraversableOnce[RdfUpdate]): Unit
	def append(updates: RdfUpdate*): Unit = appendAll(updates)

	def updates: Iterator[RdfUpdate]
	//def updatesUpTo(time: Timestamp): Iterator[RdfUpdate]

}
