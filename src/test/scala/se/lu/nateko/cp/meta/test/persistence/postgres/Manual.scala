package se.lu.nateko.cp.meta.test.persistence.postgres
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import se.lu.nateko.cp.meta.ConfigLoader
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, LoggingInstanceServer, Rdf4jInstanceServer, RdfUpdate}
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import se.lu.nateko.cp.meta.persistence.postgres.*
import se.lu.nateko.cp.meta.test.TestConfig

object Manual {

	val factory: SimpleValueFactory = SimpleValueFactory.getInstance()

	def getLog: PostgresRdfLog = {
		val config = ConfigLoader.default
		PostgresRdfLog("rdflog", config.rdfLog, factory)
	}

	def getServer: InstanceServer = new LoggingInstanceServer(TestConfig.instServer, getLog)

	def fromInstFileToDb(ncycles: Int): Unit = {
		val server = TestConfig.instServer
		val log = getLog
		val stats = server.access: conn ?=>
			conn.getStatements(null, null, null).toIndexedSeq
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
		val repo = RdfUpdateLogIngester.ingestIntoMemory(log.updates, ctxt)
		log.close()
		new Rdf4jInstanceServer(repo, ctxt)
	}
}