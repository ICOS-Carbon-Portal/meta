package se.lu.nateko.cp.meta.test.services.upload

import scala.language.unsafeNulls

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.UploadUserErrorException
import se.lu.nateko.cp.meta.services.upload.StatementsProducer
import UploadTestFixture.{given, *}

class StatementsProducerTests extends AnyFunSpec:
	it("generates document metadata and submission provenance, preserving author order"):
		withStore: s =>
			import s.*
			val authors = Seq(organization, java.net.URI("https://example.org/second-author"))
			val dto = document.copy(title = Some("Report"), description = Some("Description"), authors = authors)
			val producer = StatementsProducer(vocab, meta)
			val before = java.time.Instant.now()
			val statements = server.access(producer.getObjStatements(dto, organization))
			val after = java.time.Instant.now()
			val submission = vocab.getSubmission(hash)
			val expected = fixture("statements", "document")
			val timestamps = statements.filter(_.getPredicate == meta.prov.startedAtTime)
			assert(timestamps.size == 1)
			assert(timestamps.head.getSubject == submission)
			val started = java.time.Instant.parse(timestamps.head.getObject.stringValue)
			assert(!started.isBefore(before) && !started.isAfter(after))
			assert(statements.filterNot(timestamps.contains).toSet == expected)
			assert(statements.size == expected.size + 1)

	it("uses direct contributor links for zero or one author and ordered sequences for several"):
		withStore: s =>
			val producer = StatementsProducer(s.vocab, s.meta)
			val obj = s.vocab.getStaticObject(hash)
			for count <- 0 to 2 do
				val authors = Seq(organization, java.net.URI("https://example.org/author")).take(count)
				var sequenceRequested = false
				val result = producer.getContribStatements(s.meta.dcterms.creator, authors, obj, {
					sequenceRequested = true
					s.vocab.getDocContribList(hash)
				}).toSeq
				assert(sequenceRequested == (count > 1))
				assert(result.size == (if count > 1 then count + 2 else count))
				if count == 1 then assert(result.head.getObject == s.factory.createIRI(organization.toString))

	it("links full replacements directly and partial replacements through a shared collection"):
		withStore: s =>
			import s.*
			val producer = StatementsProducer(vocab, meta)
			val coll = vocab.getNextVersionColl(previous)
			val replacement = document.copy(isNextVersionOf = Some(Left(previous)))
			val direct = server.access(producer.getObjStatements(replacement, organization))
			assert(fixture("statements", "direct-replacement").subsetOf(direct.toSet))
			val partial = replacement.copy(references = Some(references.copy(partialUpload = Some(true))))
			val first = server.access(producer.getObjStatements(partial, organization))
			assert(first.filter(_.getSubject == coll).toSet == fixture("statements", "partial-first"))
			assert(!fixture("statements", "direct-replacement").subsetOf(first.toSet))
			loadFixture("statements", "partial-existing")
			val subsequent = server.access(producer.getObjStatements(partial, organization))
			assert(subsequent.filter(_.getSubject == coll).toSet == fixture("statements", "partial-subsequent"))
			intercept[UploadUserErrorException]:
				server.access(producer.getObjStatements(partial.copy(isNextVersionOf = Some(Right(Seq(previous, other)))), organization))

	it("generates station acquisition metadata, trims keywords, and includes only future moratoria"):
		withStore: s =>
			import s.*
			val producer = StatementsProducer(vocab, meta)
			val future = java.time.Instant.now().plusSeconds(86400)
			for (keywords, moratorium, expectedKeywords, hasMoratorium) <- Seq(
				(Some(Seq(" CO2 ", "", "  ", "temperature")), Some(future), Some("CO2,temperature"), true),
				(Some(Seq("", "  ")), Some(java.time.Instant.EPOCH), None, false),
				(None, None, None, false)
			) do
				val dto = dataObject.copy(references = Some(references.copy(keywords = keywords, moratorium = moratorium)))
				val statements = server.access(producer.getObjStatements(dto, organization))
				assert(fixture("statements", "station-acquisition").subsetOf(statements.toSet))
				assert(statements.filter(_.getPredicate == meta.hasKeywords).map(_.getObject.stringValue) == expectedKeywords.toSeq)
				val embargo = statements.filter(_.getPredicate == meta.prov.endedAtTime)
				assert(embargo == (if hasMoratorium then Seq(factory.createStatement(vocab.getSubmission(hash), meta.prov.endedAtTime, vocab.lit(future))) else Nil))

	it("generates collection membership, provenance, and previous-version links"):
		withStore: s =>
			import s.*
			val coll = vocab.getCollection(hash)
			val member = java.net.URI(vocab.getStaticObject(other).stringValue)
			val dto = se.lu.nateko.cp.meta.StaticCollectionDto("test", Seq(member), "Collection", Some("Description"),
				Some(Left(previous)), None, Some(other), None)
			val statements = StatementsProducer(vocab, meta).getCollStatements(dto, coll, organization)
			val expected = fixture("statements", "collection")
			assert(statements.toSet == expected)
			assert(statements.size == expected.size)
