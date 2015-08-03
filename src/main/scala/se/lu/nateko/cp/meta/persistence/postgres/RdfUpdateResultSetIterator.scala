package se.lu.nateko.cp.meta.persistence.postgres

import java.sql.ResultSet

import org.openrdf.model.URI
import org.openrdf.model.Value
import org.openrdf.model.ValueFactory

import se.lu.nateko.cp.meta.instanceserver.RdfUpdate

class RdfUpdateResultSetIterator(rs: ResultSet, factory: ValueFactory, closer: () => Unit) extends ResultSetIterator[RdfUpdate](rs){

	override protected def construct(rs: ResultSet): RdfUpdate = {
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

		RdfUpdate(statement, isAssertion)
	}

	override protected def closeInternal(): Unit = closer()

	private def getUri(i: Int): URI = factory.createURI(rs.getString(i))
}
