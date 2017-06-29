package se.lu.nateko.cp.meta.test.persistence.postgres

import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import se.lu.nateko.cp.meta.persistence.postgres._
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.LoggingInstanceServer
import se.lu.nateko.cp.meta.test.TestConfig
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import se.lu.nateko.cp.meta.ConfigLoader

object Manual {

	val factory = SimpleValueFactory.getInstance()

	def getLog: PostgresRdfLog = {
		val config = ConfigLoader.default
		PostgresRdfLog("rdflog", config.rdfLog, factory)
	}

	def getServer: InstanceServer = new LoggingInstanceServer(TestConfig.instServer, getLog)

	def fromInstFileToDb(ncycles: Int): Unit = {
		val server = TestConfig.instServer
		val log = getLog
		val stats = server.getStatements(None, None, None).toIndexedSeq
		val assertions = stats.map(RdfUpdate(_, true))
		val retractions = stats.map(RdfUpdate(_, false))

		log.dropLog()
		log.initLog()

		log.appendAll(assertions)

		for(_ <- 1 to ncycles){
			log.appendAll(retractions)
			log.appendAll(assertions)
		}
		log.close()
	}

	def serverFromLog: InstanceServer = {
		val log = getLog
		val ctxt = factory.createIRI(TestConfig.instOntUri)
		val repo = RdfUpdateLogIngester.ingest(log.updates, ctxt)
		log.close()
		new SesameInstanceServer(repo, ctxt)
	}
}