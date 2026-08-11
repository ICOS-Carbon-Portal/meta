package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.RdfStoreConfig
import se.lu.nateko.cp.meta.rdfstore.persistence.RdfLogReader
import se.lu.nateko.cp.meta.rdfstore.persistence.postgres.PostgresRdfLogReader

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
	final case class ReplayPolicy(
		restoreOnFresh: Boolean = true,
		restoreOnRegularStart: Boolean = false,
		fromId: Option[Int] = None
	):
		def shouldRestore(isFreshStore: Boolean): Boolean =
			if isFreshStore then restoreOnFresh else restoreOnRegularStart

	final case class Binding(name: String, context: IRI, log: RdfLogReader, replay: ReplayPolicy)

	def apply(storeConfig: RdfStoreConfig, factory: ValueFactory): RdfLogManager =
		val bindings = storeConfig.rdfLogs.toSeq.map: (name, context) =>
			val fromId = storeConfig.rdfLogRestoreFromId.get(name)
			Binding(
				name,
				factory.createIRI(context.toString),
				PostgresRdfLogReader(name, storeConfig.rdfLog, factory),
				ReplayPolicy(
					restoreOnFresh = true,
					restoreOnRegularStart = fromId.isDefined,
					fromId = fromId
				)
			)
		new RdfLogManager(bindings)

end RdfLogManager
