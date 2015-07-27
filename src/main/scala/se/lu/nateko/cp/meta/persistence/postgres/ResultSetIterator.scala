package se.lu.nateko.cp.meta.persistence.postgres

import java.sql.ResultSet

import org.openrdf.model.URI
import org.openrdf.model.Value
import org.openrdf.model.ValueFactory

import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.persistence.RdfUpdate

class ResultSetIterator(rs: ResultSet, factory: ValueFactory, closer: () => Unit) extends CloseableIterator[RdfUpdate]{

	private[this] var doesHaveNext = rs.next()
	private[this] var closed = false

	def close(): Unit = if(!closed){
		closed = true
		rs.close()
		closer()
	}

	private def getUri(i: Int): URI = factory.createURI(rs.getString(i))

	def next(): RdfUpdate = {
		val tripleType = rs.getShort(3)
		val objString = rs.getString(6)

		val obj: Value = tripleType match{
			case 0 => //object is a URI
				factory.createURI(objString)
			case 1 => //object is a typed literal
				val litDatatype = getUri(7)
				factory.createLiteral(objString, litDatatype)
			case 2 => //object is a language-tagged literal
				val lang = rs.getString(7)
				factory.createLiteral(objString, lang)
		}

		val statement = factory.createStatement(getUri(4), getUri(5), obj)
		val isAssertion = rs.getBoolean(2)

		doesHaveNext = rs.next()
		if(!doesHaveNext) close()

		RdfUpdate(statement, isAssertion)
	}

	def hasNext: Boolean = !closed && doesHaveNext
}
