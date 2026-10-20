package se.lu.nateko.cp.meta.test.services.derived

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.services.citation.RemoteCitation
import se.lu.nateko.cp.meta.services.derived.{DerivedMetadata, DerivedMetadataResult}
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataJsonProtocol.given
import spray.json.*

import java.net.URI

class DerivedMetadataJsonTests extends AnyFunSpec:

	private val resource = URI("https://meta.fieldsites.se/objects/abc")

	private def metadata(remote: Option[RemoteCitation]) =
		DerivedMetadata(resource, References.empty, None, None, remoteCitation = remote)

	private def roundTrip(md: DerivedMetadata): DerivedMetadata = md.toJson.compactPrint.parseJson.convertTo[DerivedMetadata]

	describe("remote citation status"):

		Seq(
			RemoteCitation.NotApplicable,
			RemoteCitation.Expected(None),
			RemoteCitation.Expected(Some("Error fetching external citation"))
		).foreach: remote =>
			it(s"survives a JSON round trip when $remote"):
				assert(roundTrip(metadata(Some(remote))) === metadata(Some(remote)))

		it("is absent in metadata from rdfStores that do not report it"):
			val js = metadata(None).toJson.asJsObject
			assert(!js.fields.contains("remoteCitation"))
			assert(js.convertTo[DerivedMetadata].remoteCitation === None)

	describe("derivation errors"):

		it("survive a JSON round trip"):
			val result = DerivedMetadataResult(resource, "failed", None, Seq("Expected exactly one value"))
			assert(result.toJson.compactPrint.parseJson.convertTo[DerivedMetadataResult] === result)

		it("are empty in results from rdfStores that do not report them"):
			val js = JsObject("resource" -> JsString(resource.toString), "status" -> JsString("notFound"))
			assert(js.convertTo[DerivedMetadataResult] === DerivedMetadataResult(resource, "notFound", None))
