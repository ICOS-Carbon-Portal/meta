package se.lu.nateko.cp.meta.rdfstore

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.core.data.References
import se.lu.nateko.cp.meta.services.citation.RemoteCitation
import se.lu.nateko.cp.meta.rdfstore.DerivedMetadataJsonProtocol.given
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

	describe("derived metadata response"):

		it("survives a JSON round trip"):
			val response = DerivedMetadataResponse(version = 1, Seq(
				DerivedMetadataResult(resource, "ready", Some(metadata(Some(RemoteCitation.Expected(None))))),
				DerivedMetadataResult(URI("https://meta.icos-cp.eu/objects/missing"), "notFound", None)
			))
			assert(response.toJson.compactPrint.parseJson.convertTo[DerivedMetadataResponse] === response)
