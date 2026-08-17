package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import spray.json.*

import java.net.URI

/**
 * rdfStore's own model of the RDF named graphs citation inputs are read from (task 25, stage 2).
 *
 * This replaces the earlier `cpmeta.instanceServers`-shaped copy. rdfStore neither creates nor
 * administers `InstanceServer`s from this configuration - it only needs resolved graph scopes to
 * build `RdfLenses` - so the configuration is named and shaped after that concern, lives under
 * `rdfStore.citationGraphs`, and carries no journalling, ingestion, replay, metaflow, ontology or
 * editor settings.
 *
 * The duplication of graph topology between the two applications is deliberate: it is a deployment
 * contract between independently startable applications, not a shared configuration object.
 */

/**
 * A graph scope to read from. `primaryContext` is what `RdfLens.mkLens` needs as its primary
 * context; the field is *not* called `writeContext` any more, because nothing in rdfStore writes
 * through these lenses - that name was residue from the mutable `InstanceServer` model.
 *
 * `readContexts` is resolved at parse time (absent or empty means `Seq(primaryContext)`), so the
 * runtime type never carries an optional read-context list.
 */
case class ReadGraphConfig(primaryContext: URI, readContexts: Seq[URI])

/** Data-object graphs are chosen by object format, after the format has been read globally. */
case class DataObjectGraphDefinition(label: String, format: URI)

case class DataObjectGraphConfig(
	commonReadContexts: Seq[URI],
	uriPrefix: URI,
	definitions: Seq[DataObjectGraphDefinition]
):
	/** The graph a data object of this `format` lives in, i.e. `uriPrefix` + the graph's label. */
	def primaryContext(defn: DataObjectGraphDefinition): URI =
		new URI(uriPrefix.toString + defn.label + "/")

case class CitationGraphsConfig(
	collections: Map[Envri, ReadGraphConfig],
	documents: Map[Envri, ReadGraphConfig],
	dataObjects: Map[Envri, DataObjectGraphConfig]
):
	def envris: Set[Envri] = collections.keySet ++ documents.keySet ++ dataObjects.keySet

	/**
	 * Every configured ENVRI must have all three graph scopes. The old server-ID indirection could
	 * silently drop an ENVRI whose referenced instance-server id was missing (a `flatMap` with no
	 * error branch); here a mismatch fails startup instead.
	 */
	def validated: CitationGraphsConfig =
		val problems = envris.toSeq.sortBy(_.toString).flatMap: envri =>
			Seq(
				Option.when(!collections.contains(envri))(s"$envri has no collection graph scope"),
				Option.when(!documents.contains(envri))(s"$envri has no document graph scope"),
				Option.when(!dataObjects.contains(envri))(s"$envri has no data-object graph scopes"),
				dataObjects.get(envri).filter(_.definitions.isEmpty).map: _ =>
					s"$envri has no data-object graph definitions"
			).flatten
		if problems.isEmpty then this
		else throw new Exception(
			"Incomplete rdfStore.citationGraphs configuration: " + problems.mkString("; ")
		)

object CitationGraphsConfigJsonProtocol extends se.lu.nateko.cp.meta.core.CommonJsonSupport:
	import DefaultJsonProtocol.*
	import se.lu.nateko.cp.meta.core.MetaCoreConfig.given

	given RootJsonFormat[DataObjectGraphDefinition] = jsonFormat2(DataObjectGraphDefinition.apply)
	given RootJsonFormat[DataObjectGraphConfig] = jsonFormat3(DataObjectGraphConfig.apply)

	/** Hand-written so that `readContexts` resolves to a non-optional value while parsing. */
	given RootJsonFormat[ReadGraphConfig] with
		def write(conf: ReadGraphConfig): JsValue = JsObject(
			"primaryContext" -> conf.primaryContext.toJson,
			"readContexts" -> conf.readContexts.toJson
		)
		def read(value: JsValue): ReadGraphConfig =
			val fields = value.asJsObject("expected an object with a primaryContext").fields
			val primary = fields.getOrElse("primaryContext",
				deserializationError("missing primaryContext in a citation graph scope")
			).convertTo[URI]
			val read = fields.get("readContexts").map(_.convertTo[Seq[URI]]).filter(_.nonEmpty)
			ReadGraphConfig(primary, read.getOrElse(Seq(primary)))

	given RootJsonFormat[CitationGraphsConfig] = jsonFormat3(CitationGraphsConfig.apply)

end CitationGraphsConfigJsonProtocol
