package se.lu.nateko.cp.meta.test.services.upload

import scala.language.unsafeNulls

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.{CpmetaJsonProtocol, DataProductionDto}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.OptionalOneOrSeq
import spray.json.*
import UploadTestFixture.{hash, other, production}

class DataProductionDtoJsonTests extends AnyFunSpec with CpmetaJsonProtocol{

	private def docsJson(docs: OptionalOneOrSeq[Sha256Sum]): Option[JsValue] =
		production(docs).toJson.asJsObject.fields.get("documentation")

	private def withDocsField(docs: JsValue): JsValue =
		JsObject(production(None).toJson.asJsObject.fields + ("documentation" -> docs))

	describe("serializing production documentation"){
		it("omits the field when there is no documentation"){
			assert(docsJson(None) == None)
		}
		it("writes a single documentation as a plain string"){
			assert(docsJson(Some(Left(hash))) == Some(JsString(hash.base64Url)))
		}
		it("writes several documentations as an array"){
			assert(docsJson(Some(Right(Seq(hash, other)))) ==
				Some(JsArray(JsString(hash.base64Url), JsString(other.base64Url))))
		}
	}

	describe("parsing production documentation"){
		it("round-trips None, a single value and a list"){
			for (docs <- Seq[OptionalOneOrSeq[Sha256Sum]](None, Some(Left(hash)), Some(Right(Seq(hash, other))))) {
				withClue(s"docs=$docs: "){
					assert(production(docs).toJson.convertTo[DataProductionDto] == production(docs))
				}
			}
		}
		it("accepts the old single-string payload, so existing clients keep working"){
			val parsed = withDocsField(JsString(hash.base64Url)).convertTo[DataProductionDto]
			assert(parsed.documentation == Some(Left(hash)))
		}
		it("reads a one-element array as a list, not as a single value (not normalized)"){
			val parsed = withDocsField(JsArray(JsString(hash.base64Url))).convertTo[DataProductionDto]
			assert(parsed.documentation == Some(Right(Seq(hash))))
		}
		it("reads an empty array as an empty list, not as None (not normalized)"){
			val parsed = withDocsField(JsArray()).convertTo[DataProductionDto]
			assert(parsed.documentation == Some(Right(Seq())))
		}
	}
}
