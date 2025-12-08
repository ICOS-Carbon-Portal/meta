package se.lu.nateko.cp.meta.services.sparql.magic

import scala.language.unsafeNulls

// import org.eclipse.rdf4j.sail.lmdb.LmdbStore
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig
import org.eclipse.rdf4j.sail.nativerdf.NativeStore
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.RdfStorageConfig

import java.nio.file.{FileVisitOption, Files, Paths}
import se.lu.nateko.cp.meta.prototype.ntriples.SqlSail

object StorageSail:
	private val log = LoggerFactory.getLogger(getClass())

	def apply(conf: RdfStorageConfig): (Boolean, MainSail) =
		val subFolder = if conf.lmdb.isDefined then "lmdb" else "native"
		val storageDir = Paths.get(conf.path).resolve(subFolder)
		val didNotExist = !Files.exists(storageDir)

		def storageFiles = Files.walk(storageDir, FileVisitOption.FOLLOW_LINKS)
			.filter(Files.isRegularFile(_))

		if(didNotExist)
			Files.createDirectories(storageDir)
		else if(conf.recreateAtStartup){
			log.info("Purging the current RDF storage")
			storageFiles.forEach(Files.delete)
		}

		val isFreshInit = didNotExist || conf.recreateAtStartup || !storageFiles.findAny.isPresent

		if(isFreshInit) log.warn(
			"ATTENTION: THIS IS A FRESH INIT OF META SERVICE. RESTART ON COMPLETION WITH cpmeta.rdfStorage.recreateAtStartup = false"
		)

		val forceSync = !isFreshInit
		val sail: MainSail = conf.lmdb match
			case Some(lmdb) =>
				val lmdbConf = new LmdbStoreConfig()
				lmdbConf.setForceSync(forceSync)
				lmdbConf.setTripleIndexes(conf.indices)
				lmdbConf.setAutoGrow(!isFreshInit)

				lmdbConf.setTripleDBSize:
					Math.max(lmdb.tripleDbSize, LmdbStoreConfig.TRIPLE_DB_SIZE)
				lmdbConf.setValueDBSize:
					Math.max(lmdb.valueDbSize, LmdbStoreConfig.VALUE_DB_SIZE)
				lmdbConf.setValueCacheSize:
					Math.max(lmdb.valueCacheSize, LmdbStoreConfig.VALUE_CACHE_SIZE)
				lmdbConf.setValueIDCacheSize:
					Math.max(lmdb.valueCacheSize / 2, LmdbStoreConfig.VALUE_ID_CACHE_SIZE)

				val lmdbSail = SqlSail(storageDir.toFile)
				log.info("NTriplesSail instantiated")
				lmdbSail
			case None =>
				val indices = if isFreshInit then "" else conf.indices
				val nativeSail = NativeStore(storageDir.toFile, indices)
				nativeSail.setForceSync(forceSync)
				log.info("NativeStore instantiated")
				nativeSail
		isFreshInit -> sail
	end apply
end StorageSail
