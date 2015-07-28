package se.lu.nateko.cp.meta.persistence

import org.openrdf.model.Statement
import java.io.Closeable
import java.sql.Timestamp

case class RdfUpdate(statement: Statement, isAssertion: Boolean)

trait RdfUpdateLog extends Closeable{

	def appendAll(updates: TraversableOnce[RdfUpdate]): Unit
	def append(updates: RdfUpdate*): Unit = appendAll(updates)

	def updates: Iterator[RdfUpdate]
	def updatesUpTo(time: Timestamp): Iterator[RdfUpdate]

}
