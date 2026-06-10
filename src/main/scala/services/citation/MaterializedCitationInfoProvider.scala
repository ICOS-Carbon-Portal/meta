package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.IRI
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.api.RdfLens
import se.lu.nateko.cp.meta.api.RdfLens.{DobjConn, DocConn, GlobConn}
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.{CitableItem, References, StaticObject}
import se.lu.nateko.cp.meta.instanceserver.StatementSource
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.Validated

import spray.json.*

/**
 * Supplies bibliographic [[References]] by reading what the citations service
 * has materialized into the triplestore (the `hasBiblioInfo` JSON literal in
 * the derived citation graph), rather than computing them on the fly.
 *
 * This is the meta service's implementation of [[CitationInfoProvider]]: the
 * live computation (DataCite calls, citation-string assembly) now lives in the
 * standalone citations service. For freshly uploaded objects whose citations
 * have not been materialized yet, the provider falls back to the references the
 * caller already assembled (typically empty). The DOI-minting path, which needs
 * non-stale data, retrieves fresh objects from the citations service instead.
 */
class MaterializedCitationInfoProvider(vocab: CpVocab, metaVocab: CpmetaVocab) extends CitationInfoProvider:
	import StatementSource.getOptionalString
	private val log = LoggerFactory.getLogger(getClass())

	def getCitationInfo(sobj: StaticObject)(using envri: Envri, conn: DocConn | DobjConn): Validated[References] =
		given GlobConn = RdfLens.global(using conn)
		readReferences(vocab.getStaticObject(sobj.hash)).map(_.getOrElse(sobj.references))

	def getItemCitationInfo(item: CitableItem, itemIri: IRI)(using GlobConn): References =
		readReferences(itemIri).result.flatten.getOrElse(item.references)

	private def readReferences(iri: IRI)(using GlobConn): Validated[Option[References]] =
		getOptionalString(iri, metaVocab.hasBiblioInfo).map: jsonOpt =>
			jsonOpt.flatMap: json =>
				try Some(json.parseJson.convertTo[References])
				catch case err: Throwable =>
					log.error(s"Could not parse materialized References for $iri", err)
					None

end MaterializedCitationInfoProvider
