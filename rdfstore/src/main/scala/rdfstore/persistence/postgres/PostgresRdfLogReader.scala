package se.lu.nateko.cp.meta.rdfstore.persistence.postgres

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, Value, ValueFactory}
import se.lu.nateko.cp.meta.{DbCredentials, DbServer, RdflogConfig}
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.rdfstore.persistence.RdfLogReader

import java.sql.{Connection, ResultSet}

/**
 * Read-only view of a Postgres-backed RDF log. Table creation is meta's job (the writer,
 * via `persistence.postgres.PostgresRdfLog`); a log that has never been written to simply
 * has nothing to restore, so `updates`/`updatesFromId` degrade to an empty iterator instead
 * of failing, rather than issuing DDL from the read side.
 */
class PostgresRdfLogReader(logName: String, serv: DbServer, creds: DbCredentials, factory: ValueFactory) extends RdfLogReader{

	private val logger = org.slf4j.LoggerFactory.getLogger(getClass)

	def updates: CloseableIterator[RdfUpdate] = readIterator(s"SELECT * FROM $logName ORDER BY id")

	def updatesFromId(id: Int): CloseableIterator[RdfUpdate] =
		readIterator(s"SELECT * FROM $logName WHERE id >= $id ORDER BY id")

	def close(): Unit = {}

	private def readIterator(query: String): CloseableIterator[RdfUpdate] =
		if(tableExists()) new ResultSetIterator(getConnection, readRdfUpdate, query)
		else{
			logger.warn(s"RDF log table '$logName' does not exist yet; nothing to restore from it")
			CloseableIterator.empty
		}

	private def tableExists(): Boolean = {
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

	private def readRdfUpdate(rs: ResultSet): RdfUpdate = {
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

	private def getConnection(): Connection = Postgres.getConnection(serv, creds).get

}

object PostgresRdfLogReader{

	def apply(name: String, conf: RdflogConfig, factory: ValueFactory) =
		new PostgresRdfLogReader(
			logName = name,
			serv = conf.server,
			creds = conf.credentials,
			factory = factory
		)

}
