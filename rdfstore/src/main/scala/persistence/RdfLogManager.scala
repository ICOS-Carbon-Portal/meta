package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.{IRI, ValueFactory}
import org.eclipse.rdf4j.repository.Repository
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.{CpmetaConfig, RdfStoreConfig}
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, LoggingInstanceServer, Rdf4jInstanceServer, RdfUpdate}
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog

import java.time.Instant
import scala.collection.mutable
import scala.collection.concurrent.TrieMap
import scala.util.Try
import scala.util.Using

final class RdfLogManager private (
	private val bindings: Seq[RdfLogManager.Binding]
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
			val replay = binding.replay
			if replay.shouldRestore(isFreshStore) then
				val fromId = replay.fromId
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
	final case class ReplayPolicy(
		restoreOnFresh: Boolean = true,
		restoreOnRegularStart: Boolean = false,
		fromId: Option[Int] = None
	):
		def shouldRestore(isFreshStore: Boolean): Boolean =
			if isFreshStore then restoreOnFresh else restoreOnRegularStart

	final case class Binding(
		name: String,
		context: IRI,
		log: RdfUpdateLog,
		replay: ReplayPolicy = ReplayPolicy()
	)

	private final case class BindingConfig(name: String, context: java.net.URI, replay: ReplayPolicy)

	private[persistence] def fromBindings(bindings: Seq[Binding]): RdfLogManager =
		new RdfLogManager(bindings)

	def apply(storeConfig: RdfStoreConfig, metaConfig: CpmetaConfig, factory: ValueFactory): RdfLogManager =
		val oldSpecificBindings = metaConfig.instanceServers.specific.values.toSeq.flatMap: conf =>
			conf.logName.map: name =>
				val skip = conf.skipLogIngestionAtStart
				BindingConfig(
					name,
					conf.writeContext,
					ReplayPolicy(
						restoreOnFresh = !skip.contains(true),
						restoreOnRegularStart = skip.contains(false),
						fromId = conf.logIngestionFromId
					)
				)

		val oldDataObjectBindings = metaConfig.instanceServers.forDataObjects.values.toSeq.flatMap: conf =>
			conf.definitions.map: definition =>
				val context = java.net.URI.create(conf.uriPrefix.toString + definition.label + "/")
				BindingConfig(
					definition.label,
					context,
					ReplayPolicy(
						restoreOnFresh = true,
						restoreOnRegularStart = definition.replayLogFrom.isDefined,
						fromId = definition.replayLogFrom
					)
				)

		val oldBindings = (oldSpecificBindings ++ oldDataObjectBindings).map: binding =>
			storeConfig.rdfLogRestoreFromId.get(binding.name).fold(binding): newOffset =>
				if binding.replay.fromId.isDefined then binding
				else binding.copy(replay = binding.replay.copy(
					restoreOnRegularStart = true,
					fromId = Some(newOffset)
				))
		val oldLogNames = oldBindings.iterator.map(_.name).toSet
		val newOnlyBindings = storeConfig.rdfLogs.toSeq.collect:
			case (name, context) if !oldLogNames.contains(name) =>
				val fromId = storeConfig.rdfLogRestoreFromId.get(name)
				BindingConfig(name, context, ReplayPolicy(true, fromId.isDefined, fromId))

		val configured = (oldBindings ++ newOnlyBindings)
			.groupBy(binding => binding.name -> binding.context)
			.toSeq
			.sortBy((key, _) => (key._1, key._2.toString))
			.map: (_, sameBindingConfigs) =>
				val policies = sameBindingConfigs.map(_.replay)
				val offsets = policies.flatMap(_.fromId).distinct
				require(offsets.size <= 1, s"Conflicting RDF-log replay offsets: ${offsets.mkString(", ")}")
				val first = sameBindingConfigs.head
				first.copy(replay = ReplayPolicy(
					restoreOnFresh = policies.exists(_.restoreOnFresh),
					restoreOnRegularStart = policies.exists(_.restoreOnRegularStart),
					fromId = offsets.headOption
				))

		val logs = configured.map(_.name).distinct
			.map(name => name -> PostgresRdfLog(name, metaConfig.rdfLog, factory))
			.toMap
		val bindings = configured.map: conf =>
			Binding(conf.name, factory.createIRI(conf.context.toString), logs(conf.name), conf.replay)

		new RdfLogManager(bindings)

end RdfLogManager
