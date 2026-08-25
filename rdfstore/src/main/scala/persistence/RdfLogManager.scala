package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.RdflogConfig
import se.lu.nateko.cp.meta.rdfstore.persistence.RdfLogReader
import se.lu.nateko.cp.meta.rdfstore.persistence.postgres.PostgresRdfLogReader
import se.lu.nateko.cp.meta.services.citation.StoreInstanceServersConfig

import java.net.URI

/**
 * Read-side owner of RDF-log restoration.
 *
 * Meta appends normal mutations to the logs through LoggingInstanceServer. rdfStore only
 * consumes those logs when it initializes a fresh store or when an operator requests a
 * configured partial replay.
 */
final class RdfLogManager private (
	private val bindings: Seq[RdfLogManager.Binding]
) extends AutoCloseable:
	import RdfLogManager.Binding

	private val logger = org.slf4j.LoggerFactory.getLogger(getClass)

	def restore(repo: Repository, isFreshStore: Boolean): Unit =
		bindings.foreach: binding =>
			if binding.replay.shouldRestore(isFreshStore) then
				val fromId = binding.replay.fromId
				val offsetDescription = fromId.fold("")(id => s" from row id $id")
				logger.info(s"Restoring RDF log '${binding.name}'$offsetDescription into graph <${binding.context}>")
				val updates = fromId.fold(binding.log.updates)(binding.log.updatesFromId)
				RdfUpdateLogIngester.ingest(
					updates,
					repo,
					cleanFirst = fromId.isEmpty,
					binding.context
				).get
				logger.info(s"Restored RDF log '${binding.name}' into graph <${binding.context}>")

	override def close(): Unit = bindings.map(_.log).distinct.foreach(_.close())

end RdfLogManager

object RdfLogManager:
	final case class LogConfig(
		name: String,
		context: URI,
		fromId: Option[Int],
		skipAtStart: Option[Boolean]
	)

	final case class ReplayPolicy(
		skipAtStart: Option[Boolean] = None,
		fromId: Option[Int] = None
	):
		def shouldRestore(isFreshStore: Boolean): Boolean =
			!skipAtStart.getOrElse(!isFreshStore)

	final case class Binding(name: String, context: IRI, log: RdfLogReader, replay: ReplayPolicy)

	def configuredLogs(instanceServers: StoreInstanceServersConfig): Seq[LogConfig] =
		val specific = instanceServers.specific.values.flatMap: config =>
			config.logName.map(LogConfig(
				_, config.writeContext, config.logIngestionFromId, config.skipLogIngestionAtStart
			))
		val dataObjects = instanceServers.forDataObjects.values.flatMap: config =>
			config.definitions.map: definition =>
				val context = URI.create(config.uriPrefix.toString + definition.label + "/")
				LogConfig(
					definition.label,
					context,
					definition.replayLogFrom,
					definition.replayLogFrom.map(_ => false)
				)

		(specific ++ dataObjects).filterNot(_.skipAtStart.contains(true)).toSeq
			.sortBy(config => (config.name, config.context.toString, config.fromId))

	def apply(
		rdfLogConfig: RdflogConfig,
		instanceServers: StoreInstanceServersConfig,
		factory: ValueFactory
	): RdfLogManager =
		val bindings = configuredLogs(instanceServers).map: config =>
			Binding(
				config.name,
				factory.createIRI(config.context.toString),
				PostgresRdfLogReader(config.name, rdfLogConfig, factory),
				ReplayPolicy(
					skipAtStart = config.skipAtStart,
					fromId = config.fromId
				)
			)
		new RdfLogManager(bindings)

end RdfLogManager
