package se.lu.nateko.cp.meta.rdfstore.persistence.postgres

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.meta.RdflogConfig
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.persistence.postgres.{DbCredentials, DbServer, Postgres, RdfUpdateResultSetIterator}
import se.lu.nateko.cp.meta.rdfstore.persistence.RdfLogReader

import java.sql.Connection

class PostgresRdfLogReader(logName: String, serv: DbServer, creds: DbCredentials, factory: ValueFactory) extends RdfLogReader{

	private val logger = org.slf4j.LoggerFactory.getLogger(getClass)

	def updates: CloseableIterator[RdfUpdate] = readIterator(s"SELECT * FROM $logName ORDER BY id")

	def updatesFromId(id: Int): CloseableIterator[RdfUpdate] =
		readIterator(s"SELECT * FROM $logName WHERE id >= $id ORDER BY id")

	def close(): Unit = {}

	private def readIterator(query: String): CloseableIterator[RdfUpdate] =
		if(tableExists()) new RdfUpdateResultSetIterator(getConnection, factory, query).plain
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
