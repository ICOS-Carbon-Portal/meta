package se.lu.nateko.cp.meta.utils.sesame

import org.openrdf.repository.Repository
import org.openrdf.repository.sail.SailRepository
import org.openrdf.sail.memory.MemoryStore
import org.openrdf.rio.RDFFormat

object Loading {

	def fromResource(path: String, baseUri: String): Repository = {
		val instStream = getClass.getResourceAsStream(path)
		val repo = new SailRepository(new MemoryStore)
		repo.initialize()
		val ontUri = repo.getValueFactory.createURI(baseUri)
		repo.transact(conn => {
			conn.add(instStream, baseUri, RDFFormat.RDFXML, ontUri)
		})
		repo
	}
}