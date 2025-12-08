package se.lu.nateko.cp.meta.prototype.ntriples

import scala.language.unsafeNulls

import java.io.File
import java.nio.channels.{FileChannel, FileLock}
import java.nio.file.StandardOpenOption
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.sail.{NotifyingSailConnection, SailException}
import org.eclipse.rdf4j.sail.helpers.AbstractNotifyingSail
import org.slf4j.LoggerFactory
import scala.util.control.NonFatal
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolverClient
import org.eclipse.rdf4j.query.algebra.evaluation.EvaluationStrategyFactory
import org.eclipse.rdf4j.query.algebra.evaluation.federation.FederatedServiceResolver

class SqlSail(dataDir: File) extends AbstractNotifyingSail with FederatedServiceResolverClient{


	override def getFederatedServiceResolver(): FederatedServiceResolver | Null = null

	override def setFederatedServiceResolver(resolver: FederatedServiceResolver | Null): Unit = {}

	private val logger = LoggerFactory.getLogger(getClass)

	private var store: Store = _
	private var lockChannel: FileChannel = _
	private var fileLock: FileLock = _

	def setEvaluationStrategyFactory(factory: EvaluationStrategyFactory): Unit = {}

	override def initializeInternal(): Unit = {
		try {
			// Ensure data directory exists
			if (!dataDir.exists()) {
				dataDir.mkdirs()
			}

			if (!dataDir.isDirectory) {
				throw new SailException(s"Not a directory: ${dataDir.getAbsolutePath}")
			}

			// Acquire file lock
			val lockFile = new File(dataDir, "store.lock")
			lockChannel = FileChannel.open(
				lockFile.toPath,
				StandardOpenOption.CREATE,
				StandardOpenOption.WRITE
			)

			fileLock = lockChannel.tryLock()
			if (fileLock == null) {
				lockChannel.close()
				throw new SailException(
					s"Could not acquire lock on ${lockFile.getAbsolutePath}. " +
					"Another process may be using this data directory."
				)
			}

			// Initialize store
			val dataFile = new File(dataDir, "data.trig")
			store = new Store(dataFile)
		} catch {
			case NonFatal(e) =>
				// Clean up on failure
				if (fileLock != null) {
					try { fileLock.release() } catch { case _: Exception => }
				}
				if (lockChannel != null) {
					try { lockChannel.close() } catch { case _: Exception => }
				}
				throw new SailException("Failed to initialize NTriplesSail", e)
		}
	}

	override def shutDownInternal(): Unit = {
		try {
			// Close store (no save - read-only)
			if (store != null) {
				store.close()
			}
		} finally {
			// Release file lock
			if (fileLock != null) {
				try {
					fileLock.release()
				} catch {
					case NonFatal(e) =>
						logger.error("Failed to release file lock", e)
				}
			}

			if (lockChannel != null) {
				try {
					lockChannel.close()
				} catch {
					case NonFatal(e) =>
						logger.error("Failed to close lock channel", e)
				}
			}
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

	override def getDataDir: File = dataDir

	override def setDataDir(dataDir: File): Unit = {
		throw new UnsupportedOperationException("Data directory cannot be changed after construction")
	}

	def getSailStore: Store = store
}
