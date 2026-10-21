package se.lu.nateko.cp.meta.test

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.persistence.RdfLogManager
import se.lu.nateko.cp.meta.persistence.RdfLogManager.ReplayPolicy

/** RdfLogManager must replay the same RDF logs as meta's instance servers did when they replayed their own logs */
class RdfLogReplayTests extends AnyFunSpec:

	private val instServers = TestConfig.metaConfig.instanceServers

	/** The replay decision that meta's instance servers used to make, given whether the RDF store was freshly created */
	private def replayedByInstanceServers(isFreshStore: Boolean) =
		MetaDb.getAllInstanceServerConfigs(instServers).values.toSeq
			.flatMap: conf =>
				conf.logName
					.filter(_ => !conf.skipLogIngestionAtStart.getOrElse(!isFreshStore))
					.map(name => (name, conf.writeContext, conf.logIngestionFromId))
			.sorted(using Ordering.by(log => (log._1, log._2.toString, log._3)))

	private def replayedByRdfLogManager(isFreshStore: Boolean) =
		RdfLogManager.configuredLogs(instServers)
			.filter(log => ReplayPolicy(log.skipAtStart, log.fromId).shouldRestore(isFreshStore))
			.map(log => (log.name, log.context, log.fromId))

	Seq(true -> "a fresh", false -> "an existing").foreach: (isFresh, store) =>
		it(s"replays the logs of the instance servers into $store RDF store"):
			assert(replayedByRdfLogManager(isFresh) === replayedByInstanceServers(isFresh))

	it("replays all logs into a fresh RDF store"):
		assert(replayedByRdfLogManager(isFreshStore = true).nonEmpty)
