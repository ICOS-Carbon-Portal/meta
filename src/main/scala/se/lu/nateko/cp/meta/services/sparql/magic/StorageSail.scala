package se.lu.nateko.cp.meta.services.sparql.magic

import akka.Done
import akka.event.LoggingAdapter
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.sail.SailConnection
import org.eclipse.rdf4j.sail.SailConnectionListener
import org.eclipse.rdf4j.sail.helpers.SailWrapper
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig
import org.eclipse.rdf4j.sail.lmdb.LmdbStore
import org.eclipse.rdf4j.sail.nativerdf.NativeStore
import se.lu.nateko.cp.meta.RdfStorageConfig
import se.lu.nateko.cp.meta.services.citation.*
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex.IndexData
import se.lu.nateko.cp.meta.utils.async.ok

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Paths
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success

object StorageSail:
	def apply(conf: RdfStorageConfig, log: LoggingAdapter): (Boolean, MainSail) =
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

		if(isFreshInit) log.warning(
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

				val lmdbSail = LmdbStore(storageDir.toFile, lmdbConf)
				log.info("LmdbStore instantiated")
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