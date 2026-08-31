package se.lu.nateko.cp.meta.services.sparql.magic

import scala.language.unsafeNulls

import org.eclipse.rdf4j.sail.lmdb.LmdbStore
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig
import org.eclipse.rdf4j.sail.nativerdf.NativeStore
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.RdfStorageConfig

import java.nio.file.Paths

object StorageSail:
	private val log = LoggerFactory.getLogger(getClass())

	/**
	 * @param bulkLoad tunes the storage for a re-runnable bulk ingest (RDF-log replay) instead of
	 *   for serving traffic. Must not be used by a process that stays up to serve queries: it
	 *   trades per-commit durability for ingest speed.
	 */
	def apply(conf: RdfStorageConfig, bulkLoad: Boolean = false): MainSail =
		val subFolder = if conf.lmdb.isDefined then "lmdb" else "native"
		val storageDir = Paths.get(conf.path).resolve(subFolder)

		// a log replay is re-runnable from scratch, so an fsync per commit buys nothing there, and
		// costs a lot on the mass ingest that follows. Serving traffic is never re-runnable, not
		// even into a store that starts out empty, so it always gets the durable setting.
		val forceSync = !bulkLoad
		if(!forceSync) log.info("Opening the RDF storage tuned for bulk ingest (forceSync off)")
		val sail: MainSail = conf.lmdb match
			case Some(lmdb) =>
				val lmdbConf = new LmdbStoreConfig()
				lmdbConf.setForceSync(forceSync)
				lmdbConf.setTripleIndexes(conf.indices)
				lmdbConf.setAutoGrow(!bulkLoad)

				lmdbConf.setTripleDBSize:
					Math.max(lmdb.tripleDbSize, LmdbStoreConfig.TRIPLE_DB_SIZE)
				lmdbConf.setValueDBSize:
					Math.max(lmdb.valueDbSize, LmdbStoreConfig.VALUE_DB_SIZE)
				lmdbConf.setValueCacheSize:
					Math.max(lmdb.valueCacheSize, LmdbStoreConfig.VALUE_CACHE_SIZE)
				lmdbConf.setValueIDCacheSize:
					Math.max(lmdb.valueCacheSize / 2, LmdbStoreConfig.VALUE_ID_CACHE_SIZE)

				val lmdbSail = LmdbStore(storageDir.toFile, lmdbConf)
				log.info("LmdbStore instantiated")
				lmdbSail
			case None =>
				val indices = if bulkLoad then "" else conf.indices
				val nativeSail = NativeStore(storageDir.toFile, indices)
				nativeSail.setForceSync(forceSync)
				log.info("NativeStore instantiated")
				nativeSail
		sail
	end apply
end StorageSail
