package se.lu.nateko.cp.meta.api

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.meta.MetaFlowConfig
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.Validated.getOrElseV
import se.lu.nateko.cp.meta.utils.rdf4j.toRdf

import java.net.URI

import RdfLens.*


type RdfLens[C <: TriplestoreConnection] = TriplestoreConnection ?=> C

class RdfLenses(
	val metaInstances: Map[Envri, MetaLens],
	cpMetaInstances: Map[String, CpLens],
	val collections: Map[Envri, CollLens],
	val documents: Map[Envri, DocLens],
	val dobjPerFormat: Map[Envri, Map[URI, DobjLens]]
):

	def metaInstanceLens(using Envri): Validated[MetaLens] = forEnvri(metaInstances, "metadata instances")

	def collectionLens  (using Envri): Validated[CollLens] = forEnvri(collections, "collections")

	def documentLens    (using Envri): Validated[DocLens] = forEnvri(documents, "documents")

	def dataObjectLens(dobjFormat: URI)(using envri: Envri): Validated[DobjLens] =
		forEnvri(dobjPerFormat, "per-data-object-format").flatMap: form2Lens =>
			new Validated(form2Lens.get(dobjFormat)).require:
				s"No RDF graphs were configured for data objects of format $dobjFormat for ENVRI $envri"

	def cpLens(metaFlow: MetaFlowConfig): Validated[CpLens] =
		val servId = metaFlow.cpMetaInstanceServerId
		cpMetaInstances.get(servId).getOrElseV:
			Validated.error(
				s"Server configuration error. No InstanceServer for " +
				"portal's own metadata RDF graph, with id $servId"
			)

	private def forEnvri[T](m: Map[Envri, T], errorTip: String)(using envri: Envri): Validated[T] =
		new Validated(m.get(envri)).require:
			s"ENVRI $envri or its $errorTip RDF graphs were not configured properly"


object RdfLens:
	opaque type GlobConn <: TriplestoreConnection = TriplestoreConnection
	opaque type MetaConn >: GlobConn <: TriplestoreConnection = TriplestoreConnection
	opaque type DobjConn >: GlobConn <: MetaConn = TriplestoreConnection
	opaque type DocConn >: GlobConn <: MetaConn = TriplestoreConnection
	opaque type CollConn >: GlobConn <: MetaConn = TriplestoreConnection
	//opaque type EnvriMetaConn >: GlobConn <: MetaConn = TriplestoreConnection
	opaque type CpMetaConn >: GlobConn <: MetaConn = TriplestoreConnection

	type ItemConn = DobjConn | DocConn | CollConn

	type MetaLens = RdfLens[MetaConn]
	type CollLens = RdfLens[CollConn]
	type DocLens = RdfLens[DocConn]
	type DobjLens = RdfLens[DobjConn]
	type GlobLens = RdfLens[GlobConn]
	//type EnvriLens = RdfLens[EnvriMetaConn]
	type CpLens = RdfLens[CpMetaConn]

	def metaLens(primaryCtxt: URI, readCtxts: Seq[URI]): MetaLens = mkLens[MetaConn](primaryCtxt, readCtxts, identity)
	def collLens(primaryCtxt: URI, readCtxts: Seq[URI]): CollLens = mkLens[CollConn](primaryCtxt, readCtxts, identity)
	def docLens(primaryCtxt: URI, readCtxts: Seq[URI]): DocLens = mkLens[DocConn](primaryCtxt, readCtxts, identity)
	def dobjLens(primaryCtxt: URI, readCtxts: Seq[URI]): DobjLens = mkLens[DobjConn](primaryCtxt, readCtxts, identity)
	def cpLens(primaryCtxt: URI, readCtxts: Seq[URI]): CpLens = mkLens[CpMetaConn](primaryCtxt, readCtxts, identity)

	val global: GlobLens = conn ?=> conn.withReadContexts(Nil)

	private def mkLens[TSC <: TriplestoreConnection](
		primary: URI, read: Seq[URI], hider: TriplestoreConnection => TSC
	): RdfLens[TSC] = conn ?=>
		given ValueFactory = conn.factory
		hider(conn.withContexts(primary.toRdf, read.map(_.toRdf)))
