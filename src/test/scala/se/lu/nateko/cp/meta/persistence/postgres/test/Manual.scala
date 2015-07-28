package se.lu.nateko.cp.meta.persistence.postgres.test

import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import org.openrdf.model.impl.ValueFactoryImpl
import se.lu.nateko.cp.meta.persistence.postgres._
import se.lu.nateko.cp.meta.persistence.RdfUpdate
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.LoggingInstanceServer
import se.lu.nateko.cp.meta.test.TestConfig
import se.lu.nateko.cp.meta.persistence.RdfUpdateLogIngester
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer

object Manual {

	val factory = new ValueFactoryImpl

	def getLog = new PostgresRdfLog(
		logName = "rdflog",
		serv = DbServer(host = "localhost", port = 5433),
		creds = DbCredentials(db = "rdflog", user = "postgres", password = "rdfpersist"),
		factory = factory
	)

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
		import scala.concurrent.ExecutionContext.Implicits.global
		val log = getLog
		val ctxt = factory.createURI(TestConfig.ontUri)
		val repoFut = RdfUpdateLogIngester.ingest(log.updates, ctxt)
		val repo = Await.result(repoFut, Duration.Inf)
		log.close()
		new SesameInstanceServer(repo, ctxt)
	}
}