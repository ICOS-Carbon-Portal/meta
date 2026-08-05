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
	def diff(dirtyOlds: Seq[Statement], news: Seq[Statement], factory: ValueFactory): Seq[RdfUpdate] = {
		val olds = dirtyOlds.map(s => factory.createStatement(s.getSubject, s.getPredicate, s.getObject))

		olds.diff(news).map(RdfUpdate(_, false)) ++
		news.diff(olds).map(RdfUpdate(_, true))
	}
}

object RdfAssertion {
	def unapply(update: RdfUpdate): Option[Statement] = {
		if (update.isAssertion) {
			Some(update.statement)
		} else {
			None
		}
	}
}

object RdfRetraction {
	def unapply(update: RdfUpdate): Option[Statement] = {
		if (!update.isAssertion) {
			Some(update.statement)
		} else {
			None
		}
	}
}
