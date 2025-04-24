package se.lu.nateko.cp.meta.persistence.postgres

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.model.{IRI, Literal, ValueFactory}
import se.lu.nateko.cp.meta.RdflogConfig
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.persistence.RdfUpdateLog

import java.sql.{BatchUpdateException, PreparedStatement, Timestamp}
import java.time.Instant

class PostgresRdfLog(logName: String, serv: DbServer, creds: DbCredentials, factory: ValueFactory) extends RdfUpdateLog{

	if(!isInitialized) initLog()

	def appendAll(updates: IterableOnce[RdfUpdate]): Unit = {
		//TODO Use a pool of connections/prepared statements for better performance

		val appendPs: PreparedStatement = getConnection().prepareStatement(
			s"""INSERT INTO $logName (tstamp, "ASSERTION", "TYPE", "SUBJECT", "PREDICATE", "OBJECT", "LITATTR") VALUES (?, ?, ?, ?, ?, ?, ?)"""
		)
		try{
			val Seq(tstamp, assertion, typeCol, subject, predicate, objectCol, litattr) = 1 to 7
			appendPs.clearBatch()

			for(update <- updates.iterator){
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
		} catch{
			case bue: BatchUpdateException =>
				throw new Exception(bue.getMessage, bue.getNextException)
		} finally{
			appendPs.getConnection.close()
		}
	}

	private def allUpdQuery = s"SELECT * FROM $logName ORDER BY id"
	def updates: CloseableIterator[RdfUpdate] = rdfUpdateIterator(allUpdQuery).plain
	def timedUpdates: CloseableIterator[(Instant, RdfUpdate)] = rdfUpdateIterator(allUpdQuery).timed
	def updatesFromId(id: Int) = rdfUpdateIterator(s"SELECT * FROM $logName WHERE id >= $id ORDER BY id").plain

	private def rdfUpdateIterator(query: String) = new RdfUpdateResultSetIterator(getConnection, factory, query)

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
			s"""CREATE TABLE IF NOT EXISTS $logName (
				|id serial PRIMARY KEY,
				|tstamp timestamptz,
				|"ASSERTION" boolean,
				|"TYPE" smallint,
				|"SUBJECT" text,
				|"PREDICATE" text,
				|"OBJECT" text,
				|"LITATTR" text
			);""".stripMargin

		try{
			//This may produce duplicate-index error if concurrently initializing duplicate new rdflogs
			//(for example on a new empty Postgres db)
			//the reason seems to be Postgres' lame handling of concurrency during table creation
			//see also https://www.postgresql.org/message-id/CA%2BTgmoZAdYVtwBfp1FL2sMZbiHCWT4UPrzRLNnX1Nb30Ku3-gg%40mail.gmail.com
			execute(createTable)
		} catch{
			case err: org.postgresql.util.PSQLException if err.getMessage.contains(
				"duplicate key value violates unique constraint"
			) => //this is expected to happen sometimes, swallowing the exception
		}
	}

	def isInitialized: Boolean = {
		val conn = getConnection()
		try{
			val meta = conn.getMetaData
			val tblRes = meta.getTables(null, null, logName, null)
			val tblPresent = tblRes.next()
			tblRes.close()
			tblPresent
		}finally{
			conn.close()
		}

	}

	private def execute(statement: String): Unit = {
		val conn = getConnection()
		try{
			val st = conn.createStatement
			st.execute(statement)
			st.close()
		}finally{
			conn.close()
		}
	}

	private def getConnection() = Postgres.getConnection(serv, creds).get

	private def safeDatatype(lit: Literal): String =
		if(lit.getDatatype == null) XSD.STRING.stringValue
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
