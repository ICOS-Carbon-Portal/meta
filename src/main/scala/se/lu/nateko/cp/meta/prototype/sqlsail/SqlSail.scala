package se.lu.nateko.cp.meta.prototype.ntriples

import scala.language.unsafeNulls

import java.io.File
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.sail.{NotifyingSailConnection, SailException}
import org.eclipse.rdf4j.sail.helpers.AbstractNotifyingSail
import org.slf4j.LoggerFactory
import scala.util.control.NonFatal
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolverClient
import org.eclipse.rdf4j.query.algebra.evaluation.EvaluationStrategyFactory
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver
import se.lu.nateko.cp.meta.persistence.postgres.{DbServer, DbCredentials}
import com.typesafe.config.ConfigFactory

object SqlSail {
	def apply(dataDir: File): SqlSail = {
		// Load configuration from application.conf
		val config = ConfigFactory.load()
		val server = DbServer(
			config.getString("cpmeta.sqlSail.server.host"),
			config.getInt("cpmeta.sqlSail.server.port")
		)
		val credentials = DbCredentials(
			config.getString("cpmeta.sqlSail.credentials.db"),
			config.getString("cpmeta.sqlSail.credentials.user"),
			config.getString("cpmeta.sqlSail.credentials.password")
		)
		val tableName = if (config.hasPath("cpmeta.sqlSail.tableName")) {
			config.getString("cpmeta.sqlSail.tableName")
		} else {
			"triples"
		}
		new SqlSail(server, credentials, tableName)
	}

	def apply(server: DbServer, credentials: DbCredentials, tableName: String = "triples"): SqlSail = {
		new SqlSail(server, credentials, tableName)
	}
}

class SqlSail(server: DbServer, credentials: DbCredentials, tableName: String = "triples") extends AbstractNotifyingSail with FederatedServiceResolverClient{


	override def getFederatedServiceResolver(): FederatedServiceResolver | Null = null

	override def setFederatedServiceResolver(resolver: FederatedServiceResolver | Null): Unit = {}

	private val logger = LoggerFactory.getLogger(getClass)

	private var store: Store = _

	def setEvaluationStrategyFactory(factory: EvaluationStrategyFactory): Unit = {}

	override def initializeInternal(): Unit = {
		try {
			// Initialize store with database configuration
			store = new Store(server, credentials, tableName)

			// Test database connection
			val conn = store.getConnection()
			try {
				// Verify table exists
				val meta = conn.getMetaData
				val rs = meta.getTables(null, null, tableName, null)
				if (!rs.next()) {
					throw new SailException(s"Table $tableName does not exist")
				}
				rs.close()
			} finally {
				conn.close()
			}
		} catch {
			case NonFatal(e) =>
				throw new SailException("Failed to initialize SqlSail", e)
		}
	}

	override def shutDownInternal(): Unit = {
		try {
			// Close store
			if (store != null) {
				store.close()
			}
		} catch {
			case NonFatal(e) =>
				logger.error("Failed to shutdown store", e)
		}
	}

	override def getConnectionInternal(): NotifyingSailConnection = {
		new SqlSailConnection(this, store)
	}

	override def isWritable(): Boolean = false

	override def getValueFactory: ValueFactory = {
		if (store == null) {
			throw new IllegalStateException("Sail has not been initialized")
		}
		store.getValueFactory
	}

	override def getDataDir: File = null

	override def setDataDir(dataDir: File): Unit = {
		throw new UnsupportedOperationException("SqlSail does not use a data directory")
	}

	def getSailStore: Store = store
}
