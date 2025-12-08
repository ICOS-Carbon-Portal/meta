package se.lu.nateko.cp.meta.prototype.ntriples

import scala.language.unsafeNulls

import org.eclipse.rdf4j.common.transaction.IsolationLevel
import org.eclipse.rdf4j.sail.base.{BackingSailSource, SailDataset, SailSink}

class NTriplesSailSource(store: NTriplesSailStore, explicit: Boolean) extends BackingSailSource {

	override def dataset(level: IsolationLevel): SailDataset = {
		new NTriplesSailDataset(store, explicit)
	}

	override def sink(level: IsolationLevel): SailSink = {
		new NTriplesSailSink(store, explicit)
	}
}
