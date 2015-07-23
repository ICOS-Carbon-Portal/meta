package se.lu.nateko.cp.meta.persistence

import org.openrdf.model.Statement
import java.util.concurrent.ConcurrentLinkedQueue

import scala.collection.JavaConverters._

case class RdfUpdate(statement: Statement, isAssertion: Boolean)

trait RdfUpdateLog {
	def appendAll(updates: Seq[RdfUpdate]): Unit
	def append(updates: RdfUpdate*): Unit = appendAll(updates)
	def updates: Iterator[RdfUpdate]
}

class InMemoryRdfLog extends RdfUpdateLog{

	private[this] val log = new ConcurrentLinkedQueue[RdfUpdate]()

	def appendAll(updates: Seq[RdfUpdate]): Unit = {
		log.addAll(updates.asJavaCollection)
	}

	def updates: Iterator[RdfUpdate] = log.iterator.asScala
}