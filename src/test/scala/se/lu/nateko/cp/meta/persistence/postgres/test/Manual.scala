package se.lu.nateko.cp.meta.persistence.postgres.test

import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import org.openrdf.model.impl.ValueFactoryImpl
import se.lu.nateko.cp.meta.persistence.postgres.DbCredentials
import se.lu.nateko.cp.meta.persistence.RdfUpdate
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.LoggingInstanceServer
import se.lu.nateko.cp.meta.test.TestConfig

object Manual {

	val factory = new ValueFactoryImpl

	def getLog = new PostgresRdfLog(
		logName = "rdflog",
		creds = DbCredentials(db = "rdflog", user = "postgres", password = "rdfpersist"),
		factory = factory
	)

//	val updates: Seq[RdfUpdate] = {
//		???
//	}

	def getServer: InstanceServer = new LoggingInstanceServer(TestConfig.instServer, getLog)
}