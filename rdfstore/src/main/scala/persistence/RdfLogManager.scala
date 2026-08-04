package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import org.eclipse.rdf4j.repository.Repository
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.RdfStoreConfig
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, LoggingInstanceServer, Rdf4jInstanceServer, RdfUpdate}
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog

import java.time.Instant
import scala.collection.mutable
import scala.collection.concurrent.TrieMap
import scala.util.Try
import scala.util.Using

final class RdfLogManager private (
	private val bindings: Seq[RdfLogManager.Binding],
	private val restoreFromId: Map[String, Int]
) extends AutoCloseable:
	import RdfLogManager.Binding

	private val logger = LoggerFactory.getLogger(getClass)
	private val byContext: Map[String, Binding] = bindings.map(binding => binding.context.stringValue -> binding).toMap
	private val instanceServers = TrieMap.empty[String, InstanceServer]

	def binding(context: IRI): Option[Binding] = byContext.get(context.stringValue)

	def applyAll(repo: Repository, context: IRI, updates: Seq[RdfUpdate]): Try[Unit] =
		val server = instanceServers.getOrElseUpdate(context.stringValue, createServer(repo, context))
		server.applyAll(updates)()

	/**
	 * Replays logs one at a time. This is deliberately serialized: NativeStore
	 * has previously crashed under the parallel write load produced by initial
	 * RDF-log restoration.
	 */
	def restore(repo: Repository, isFreshStore: Boolean): Unit =
		bindings.foreach: binding =>
			val fromId = restoreFromId.get(binding.name)
			if isFreshStore || fromId.isDefined then
				val offsetDescription = fromId.fold("")(id => s" from row id $id")
				logger.info(
					s"Restoring RDF log '${binding.name}'$offsetDescription into graph <${binding.context}>"
				)
				val updates = fromId.fold(binding.log.updates)(binding.log.updatesFromId)
				RdfUpdateLogIngester.ingest(
					updates,
					repo,
					cleanFirst = fromId.isEmpty,
					binding.context
				).get
				logger.info(
					s"Restored RDF log '${binding.name}' into graph <${binding.context}>"
				)

	def history(contexts: Seq[IRI]): Seq[(Instant, RdfUpdate)] =
		val seen = mutable.HashSet.empty[String]
		contexts.flatMap(binding).filter(binding => seen.add(binding.name)).flatMap: binding =>
			Using.resource(binding.log.timedUpdates)(_.toIndexedSeq)

	override def close(): Unit = bindings.map(_.log).distinct.foreach(_.close())

	private def createServer(repo: Repository, context: IRI): InstanceServer =
		val inner = Rdf4jInstanceServer(repo, context)
		binding(context).fold[InstanceServer](inner): binding =>
			LoggingInstanceServer(inner, binding.log)

end RdfLogManager

object RdfLogManager:
	final case class Binding(name: String, context: IRI, log: RdfUpdateLog)

	private[persistence] def fromBindings(bindings: Seq[Binding]): RdfLogManager =
		new RdfLogManager(bindings, Map.empty)

	def apply(storeConfig: RdfStoreConfig, factory: ValueFactory): RdfLogManager =
		val configured = storeConfig.rdfLogs.toSeq.sortBy(_._1)
		require(
			configured.map(_._2).distinct.size == configured.size,
			"rdfStore.rdfLogs must map each named graph to exactly one RDF log"
		)

		val bindings = configured.map: (name, context) =>
			Binding(
				name,
				factory.createIRI(context.toString),
				PostgresRdfLog(name, storeConfig.rdfLog, factory)
			)

		new RdfLogManager(bindings, storeConfig.rdfLogRestoreFromId)

end RdfLogManager
