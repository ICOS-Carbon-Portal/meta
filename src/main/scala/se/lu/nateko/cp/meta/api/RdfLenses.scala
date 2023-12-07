package se.lu.nateko.cp.meta.api

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.rdf4j.toRdf

import java.net.URI


type RdfLens = TriplestoreConnection ?=> TriplestoreConnection

class RdfLenses(
	val metaInstances: Map[Envri, RdfLens],
	val collections: Map[Envri, RdfLens],
	val documents: Map[Envri, RdfLens],
	val dobjPerFormat: Map[Envri, Map[URI, RdfLens]]
):

	def metaInstanceLens(using Envri): Validated[RdfLens] = forEnvri(metaInstances, "metadata instances")

	def collectionLens  (using Envri): Validated[RdfLens] = forEnvri(collections, "collections")

	def documentLens    (using Envri): Validated[RdfLens] = forEnvri(documents, "documents")

	def dataObjectLens(dobjFormat: URI)(using envri: Envri): Validated[RdfLens] =
		forEnvri(dobjPerFormat, "per-data-object-format").flatMap: form2Lens =>
			new Validated(form2Lens.get(dobjFormat)).require:
				s"No RDF graphs were configured for data objects of format $dobjFormat for ENVRI $envri"

	private def forEnvri[T](m: Map[Envri, T], errorTip: String)(using envri: Envri): Validated[T] =
		new Validated(m.get(envri)).require:
			s"ENVRI $envri or its $errorTip RDF graphs were not configured properly"


object RdfLens:
	def fromContexts(primary: URI, read: Seq[URI]): RdfLens = conn ?=>
		given ValueFactory = conn.factory
		conn.withContexts(primary.toRdf, read.map(_.toRdf))
