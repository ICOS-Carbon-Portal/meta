package se.lu.nateko.cp.meta.persistence.postgres

import java.sql.ResultSet

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory

import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import java.sql.Connection
import java.time.Instant

class RdfUpdateResultSetIterator(getConn: () => Connection, factory: ValueFactory, selectQuery: String){
	def plain = new ResultSetIterator(getConn, readRdfUpdate, selectQuery)
	def timed = new ResultSetIterator(getConn, readTimedRdfUpdate, selectQuery)

	def readRdfUpdate(rs: ResultSet): RdfUpdate = {
		def getUri(colName: String): IRI = factory.createIRI(rs.getString(colName))

		val tripleType = rs.getShort("TYPE")
		val objString = rs.getString("OBJECT")

		val obj: Value = tripleType match{
			case 0 => //object is a URI
				factory.createIRI(objString)
			case 1 => //object is a typed literal
				val litDatatype = getUri("LITATTR")
				factory.createLiteral(objString, litDatatype)
			case 2 => //object is a language-tagged literal
				val lang = rs.getString("LITATTR")
				factory.createLiteral(objString, lang)
		}

		val statement = factory.createStatement(getUri("SUBJECT"), getUri("PREDICATE"), obj)
		val isAssertion = rs.getBoolean("ASSERTION")

		RdfUpdate(statement, isAssertion)
	}

	def readTimedRdfUpdate(rs: ResultSet): (Instant, RdfUpdate) = {
		val upd = readRdfUpdate(rs)
		val ts = rs.getTimestamp("tstamp").toInstant
		ts -> upd
	}
}
