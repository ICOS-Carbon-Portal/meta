package se.lu.nateko.cp.meta.persistence

import org.openrdf.model.Statement
import java.util.concurrent.ConcurrentLinkedQueue

import scala.collection.JavaConverters._

case class RdfUpdate(statement: Statement, isAssertion: Boolean)

trait RdfUpdateLog {
	def append(updates: Seq[RdfUpdate]): Unit
	def updates: Iterator[RdfUpdate]
}

class InMemoryRdfLog extends RdfUpdateLog{
	private[this] val log = new ConcurrentLinkedQueue[RdfUpdate]()

	override def append(updates: Seq[RdfUpdate]): Unit = {
		log.addAll(updates.asJavaCollection)
	}

	override def updates: Iterator[RdfUpdate] = log.iterator.asScala
}