package se.lu.nateko.cp.meta.services.citation

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.api.RdfLens.{DobjConn, DocConn, MetaConn}
import se.lu.nateko.cp.meta.core.data.{CitableItem, References, StaticObject}
import se.lu.nateko.cp.meta.services.upload.StaticObjectReferenceProvider
import se.lu.nateko.cp.meta.utils.Validated

import scala.collection.mutable.ArrayBuffer

/** Records the errors of making citation info, so that they can be reported to meta separately from
  * the errors of reading the object itself (which meta reads on its own). Not thread-safe: use one per lookup.
  */
private class CitationErrorRecorder(citer: StaticObjectReferenceProvider) extends StaticObjectReferenceProvider:
	private val recorded = ArrayBuffer.empty[String]

	def errors: Seq[String] = recorded.toSeq

	def getItemCitationInfo(item: CitableItem): References = citer.getItemCitationInfo(item)

	def getCitationInfo(item: StaticObject, specConn: MetaConn)(using Envri, DocConn | DobjConn): Validated[References] =
		// flatMap reports a thrown exception exactly as the reader's own flatMap would
		val refs = Validated.ok(()).flatMap(_ => citer.getCitationInfo(item, specConn))
		recorded ++= refs.errors
		refs
