package se.lu.nateko.cp.meta.rdfstore.persistence

import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate

import java.io.Closeable

/** Read-only counterpart of meta's `persistence.RdfUpdateLog`, for rdfStore's own log restoration. */
trait RdfLogReader extends Closeable{

	def updates: CloseableIterator[RdfUpdate]
	def updatesFromId(id: Int): CloseableIterator[RdfUpdate]

}
