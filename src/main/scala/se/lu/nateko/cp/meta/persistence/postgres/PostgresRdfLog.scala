package se.lu.nateko.cp.meta.persistence.postgres

import java.sql.PreparedStatement
import java.sql.Timestamp

import org.openrdf.model.Literal
import org.openrdf.model.URI
import org.openrdf.model.ValueFactory
import org.openrdf.model.vocabulary.XMLSchema

import se.lu.nateko.cp.meta.persistence.RdfUpdate
import se.lu.nateko.cp.meta.persistence.RdfUpdateLog

class PostgresRdfLog(logName: String, creds: DbCredentials, factory: ValueFactory) extends RdfUpdateLog{

	private[this] val appendPs: PreparedStatement = {
		val appenderConn = getConnection
		appenderConn.prepareStatement(s"INSERT INTO $logName VALUES (?, ?, ?, ?, ?, ?, ?)")
	}

	def appendAll(updates: Seq[RdfUpdate]): Unit = {
		appendPs.clearBatch()

		for(update <- updates){
			appendPs.setTimestamp(1, new Timestamp(System.currentTimeMillis))
			appendPs.setBoolean(2, update.isAssertion)
			appendPs.setString(4, update.statement.getSubject.stringValue)
			appendPs.setString(5, update.statement.getPredicate.stringValue)

			update.statement.getObject match{
				case uri: URI =>
					appendPs.setShort(3, 0) //triple type 0
					appendPs.setString(6, uri.stringValue)
				case lit: Literal if lit.getLanguage != null =>
					appendPs.setShort(3, 2) //triple type 2
					appendPs.setString(6, lit.getLabel)
					appendPs.setString(7, lit.getLanguage)
				case lit: Literal =>
					appendPs.setShort(3, 1) //triple type 1
					appendPs.setString(6, lit.getLabel)
					appendPs.setString(7, safeDatatype(lit))
			}

			appendPs.addBatch()
		}
		appendPs.executeBatch()
	}

	def updates: Iterator[RdfUpdate] = {
		val conn = getConnection
		val st = conn.createStatement
		val rs = st.executeQuery(s"SELECT * FROM $logName")
		new ResultSetIterator(rs, factory, () => {st.close; conn.close()})
	}

	def updatesUpTo(time: Timestamp): Iterator[RdfUpdate] = {
		val conn = getConnection
		val ps = conn.prepareStatement(s"SELECT * FROM $logName WHERE TIMESTAMP < ?")
		ps.setTimestamp(1, time)
		val rs = ps.executeQuery()
		new ResultSetIterator(rs, factory, () => {ps.close(); conn.close()})
	}

	def close(): Unit = appendPs.getConnection.close()

	def dropLog(): Unit = execute(s"DROP TABLE $logName")

	def initLog(): Unit = execute(
		s"CREATE TABLE $logName (" + """
			"TIMESTAMP" timestamp with time zone,
			"ASSERTION" boolean,
			"TYPE" smallint,
			"SUBJECT" text,
			"PREDICATE" text,
			"OBJECT" text,
			"LITATTR" text
			) WITH (OIDS=FALSE)""",
		s"ALTER TABLE $logName OWNER TO ${creds.user}"
	)


	private def execute(statements: String*): Unit = {
		val conn = getConnection
		val st = conn.createStatement
		statements.foreach(st.execute)
		st.close()
		conn.close()
	}

	private def getConnection = Postgres.getConnection(creds).get

	private def safeDatatype(lit: Literal): String =
		if(lit.getDatatype == null) XMLSchema.STRING.stringValue
		else lit.getDatatype.stringValue

}