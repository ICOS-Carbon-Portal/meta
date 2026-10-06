package se.lu.nateko.cp.meta.rdfstore.persistence

import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate

import java.io.Closeable

trait RdfLogReader extends Closeable{

	def updates: CloseableIterator[RdfUpdate]
	def updatesFromId(id: Int): CloseableIterator[RdfUpdate]

}
