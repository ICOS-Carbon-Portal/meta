package se.lu.nateko.cp.meta.services.sparql.magic

import akka.Done
import akka.event.LoggingAdapter
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.sail.SailConnection
import org.eclipse.rdf4j.sail.SailConnectionListener
import org.eclipse.rdf4j.sail.helpers.SailWrapper
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

object CpNativeStore:
	def apply(conf: RdfStorageConfig, log: LoggingAdapter): (Boolean, MainSail) =
		val storageDir = Paths.get(conf.path)
		val didNotExist = !Files.exists(storageDir)

		def storageFiles = Files.walk(storageDir, FileVisitOption.FOLLOW_LINKS)
			.filter(Files.isRegularFile(_))

		if(didNotExist)
			Files.createDirectories(storageDir)
		else if(conf.recreateAtStartup){
			log.info("Purging the current native RDF storage")
			storageFiles.forEach(Files.delete)
		}

		val isFreshInit = didNotExist || conf.recreateAtStartup || !storageFiles.findAny.isPresent

		if(isFreshInit) log.warning(
			"ATTENTION: THIS IS A FRESH INIT OF META SERVICE. RESTART ON COMPLETION WITH cpmeta.rdfStorage.recreateAtStartup = false"
		)

		val indices = if(isFreshInit) "" else conf.indices
		log.info("Instantiating NativeStore...")
		val nativeSail = NativeStore(storageDir.toFile, indices)
		val forceSync = !isFreshInit
		log.info(s"Setting force-sync to '$forceSync'")
		nativeSail.setForceSync(forceSync)
		isFreshInit -> nativeSail
	end apply
end CpNativeStore
