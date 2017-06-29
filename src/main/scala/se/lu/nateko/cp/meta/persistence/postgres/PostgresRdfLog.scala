package se.lu.nateko.cp.meta.persistence.postgres

import java.sql.PreparedStatement
import java.sql.Timestamp
import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.persistence.RdfUpdateLog
import se.lu.nateko.cp.meta.RdflogConfig

class PostgresRdfLog(logName: String, serv: DbServer, creds: DbCredentials, factory: ValueFactory) extends RdfUpdateLog{

	if(!isInitialized) initLog()

	def appendAll(updates: TraversableOnce[RdfUpdate]): Unit = {
		//TODO Use a pool of connections/prepared statements for better performance

		val appendPs: PreparedStatement = getConnection.prepareStatement(
			s"""INSERT INTO $logName (tstamp, "ASSERTION", "TYPE", "SUBJECT", "PREDICATE", "OBJECT", "LITATTR") VALUES (?, ?, ?, ?, ?, ?, ?)"""
		)
		try{
			val Seq(tstamp, assertion, typeCol, subject, predicate, objectCol, litattr) = 1 to 7
			appendPs.clearBatch()

			for(update <- updates){
				appendPs.setTimestamp(tstamp, new Timestamp(System.currentTimeMillis))
				appendPs.setBoolean(assertion, update.isAssertion)
				appendPs.setString(subject, update.statement.getSubject.stringValue)
				appendPs.setString(predicate, update.statement.getPredicate.stringValue)

				update.statement.getObject match{
					case uri: IRI =>
						appendPs.setShort(typeCol, 0) //triple type 0
						appendPs.setString(objectCol, uri.stringValue)
						appendPs.setString(litattr, null)

					case lit: Literal if lit.getLanguage.isPresent =>
						appendPs.setShort(typeCol, 2) //triple type 2
						appendPs.setString(objectCol, lit.getLabel)
						appendPs.setString(litattr, lit.getLanguage.get)

					case lit: Literal =>
						appendPs.setShort(typeCol, 1) //triple type 1
						appendPs.setString(objectCol, lit.getLabel)
						appendPs.setString(litattr, safeDatatype(lit))

					case _ =>
						val st = update.statement
						throw new Exception("Attempted to append an invalid triple:\n" +
							s"${st.getSubject} -> ${st.getPredicate} -> ${st.getObject}")
				}
				appendPs.addBatch()
			}
			appendPs.executeBatch()
		} finally{
			appendPs.getConnection.close()
		}
	}

	def updates: Iterator[RdfUpdate] = {
		val conn = getConnection
		val st = conn.createStatement
		val rs = st.executeQuery(s"SELECT * FROM $logName ORDER BY id")
		new RdfUpdateResultSetIterator(rs, factory, () => {st.close; conn.close()})
	}

/*	def updatesUpTo(time: Timestamp): Iterator[RdfUpdate] = {
		val conn = getConnection
		val ps = conn.prepareStatement(s"SELECT * FROM $logName WHERE tstamp <= ? ORDER BY id") //no index on time, better test in Scala
		ps.setTimestamp(1, time)
		val rs = ps.executeQuery()
		new RdfUpdateResultSetIterator(rs, factory, () => {ps.close(); conn.close()})
	}*/

	def close(): Unit = {}

	def dropLog(): Unit = execute(s"DROP TABLE IF EXISTS $logName")

	def initLog(): Unit = {

		val createTable =
			s"""CREATE TABLE $logName (
				|id serial PRIMARY KEY,
				|tstamp timestamptz,
				|"ASSERTION" boolean,
				|"TYPE" smallint,
				|"SUBJECT" text,
				|"PREDICATE" text,
				|"OBJECT" text,
				|"LITATTR" text
			);""".stripMargin

		execute(createTable)
	}

	def isInitialized: Boolean = {
		val meta = getConnection.getMetaData
		val tblRes = meta.getTables(null, null, logName, null)
		val tblPresent = tblRes.next()
		tblRes.close()
		tblPresent
	}

	private def execute(statement: String): Unit = {
		val conn = getConnection
		val st = conn.createStatement
		st.execute(statement)
		st.close()
		conn.close()
	}

	private def getConnection = Postgres.getConnection(serv, creds).get

	private def safeDatatype(lit: Literal): String =
		if(lit.getDatatype == null) XMLSchema.STRING.stringValue
		else lit.getDatatype.stringValue

}

object PostgresRdfLog{

	def apply(name: String, conf: RdflogConfig, factory: ValueFactory) =
		new PostgresRdfLog(
			logName = name,
			serv = conf.server,
			creds = conf.credentials,
			factory = factory
		)

}
