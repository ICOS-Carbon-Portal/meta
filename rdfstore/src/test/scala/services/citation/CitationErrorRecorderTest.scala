package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.RdfLens.{DobjConn, DocConn, MetaConn}
import se.lu.nateko.cp.meta.core.data.{CitableItem, References, StaticObject}
import se.lu.nateko.cp.meta.services.upload.StaticObjectReferenceProvider
import se.lu.nateko.cp.meta.test.services.external.ExternalTestObjects
import se.lu.nateko.cp.meta.utils.Validated

class CitationErrorRecorderTest extends AnyFunSpec:

	private given Envri = Envri.ICOS
	// the stub providers below do not read the store
	private given DocConn = null.asInstanceOf[DocConn]
	private val specConn = null.asInstanceOf[MetaConn]
	private val obj = ExternalTestObjects.docObject(None)

	private def provider(citation: => Validated[References]) = new StaticObjectReferenceProvider:
		def getItemCitationInfo(item: CitableItem): References = item.references
		def getCitationInfo(item: StaticObject, specConn: MetaConn)(using Envri, DocConn | DobjConn) = citation

	describe("CitationErrorRecorder"):

		it("records the errors of making the citation, and passes on the citation unchanged"):
			val citation = new Validated(Some(References.empty), Seq("first", "second"))
			val recorder = CitationErrorRecorder(provider(citation))
			val result = recorder.getCitationInfo(obj, specConn)
			assert(result.result === citation.result)
			assert(result.errors === citation.errors)
			assert(recorder.errors === Seq("first", "second"))

		it("accumulates the errors of repeated calls"):
			val recorder = CitationErrorRecorder(provider(Validated.error("failed")))
			recorder.getCitationInfo(obj, specConn)
			recorder.getCitationInfo(obj, specConn)
			assert(recorder.errors === Seq("failed", "failed"))

		it("records a thrown exception as the error that the object reader would report"):
			val recorder = CitationErrorRecorder(provider(throw IllegalStateException("boom")))
			val result = recorder.getCitationInfo(obj, specConn)
			assert(result.result.isEmpty)
			assert(recorder.errors === Seq("java.lang.IllegalStateException: boom"))

end CitationErrorRecorderTest
