package se.lu.nateko.cp.meta.persistence.postgres

import java.sql.ResultSet

import org.openrdf.model.URI
import org.openrdf.model.Value
import org.openrdf.model.ValueFactory

import se.lu.nateko.cp.meta.instanceserver.RdfUpdate

class RdfUpdateResultSetIterator(rs: ResultSet, factory: ValueFactory, closer: () => Unit) extends ResultSetIterator[RdfUpdate](rs){

	override protected def construct(rs: ResultSet): RdfUpdate = {
		val tripleType = rs.getShort("TYPE")
		val objString = rs.getString("OBJECT")

		val obj: Value = tripleType match{
			case 0 => //object is a URI
				factory.createURI(objString)
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

	override protected def closeInternal(): Unit = closer()

	private def getUri(colName: String): URI = factory.createURI(rs.getString(colName))
}
