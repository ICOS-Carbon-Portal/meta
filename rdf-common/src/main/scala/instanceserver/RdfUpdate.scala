package se.lu.nateko.cp.meta.instanceserver

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{Statement, ValueFactory}

case class RdfUpdate(statement: Statement, isAssertion: Boolean)

object RdfUpdate {
	/**
	 * Computes the RdfUpdates needed to transform `dirtyOlds` into `news`, normalizing
	 * `dirtyOlds` through `factory` first so that statement equality is well-defined.
	 * Shared by InstanceServer.applyDiff and the metadata-updater write-side services.
	 */
	def diff(dirtyOlds: Seq[Statement | RdfStatement], news: Seq[Statement], factory: ValueFactory): Seq[RdfUpdate] = {
		val olds = dirtyOlds.map:
			case s: Statement => factory.createStatement(s.getSubject, s.getPredicate, s.getObject)
			case s: RdfStatement => s.toRdf4jStatement(using factory)

		olds.diff(news).map(RdfUpdate(_, false)) ++
		news.diff(olds).map(RdfUpdate(_, true))
	}
}
