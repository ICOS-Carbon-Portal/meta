package se.lu.nateko.cp.meta.test.services.upload

import scala.language.unsafeNulls

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.Uri
import com.sun.net.httpserver.HttpServer
import eu.icoscp.envri.Envri
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.doi.core.DoiMemberConfig
import se.lu.nateko.cp.meta.DoiConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import se.lu.nateko.cp.meta.services.upload.DoiService
import se.lu.nateko.cp.meta.utils.Validated
import spray.json.*

import java.net.{InetSocketAddress, URI}
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*

class DoiServiceTests extends AnyFunSpec {

	private given ExecutionContext = ExecutionContext.global
	private given Envri = Envri.ICOS

	private val hash = Sha256Sum.fromHex("01" * 32).get
	private val objectUri = URI(s"https://meta.icos-cp.eu/objects/${hash.id}")
	private val collectionUri = URI(s"https://meta.icos-cp.eu/collections/${hash.id}")
	private val organization = Organization(
		UriResource(URI("https://meta.icos-cp.eu/resources/organizations/test"), None, Nil),
		"Test organization", None, None, None
	)
	private val document = DocObject(
		hash = hash, accessUrl = None, pid = None, doi = None,
		fileName = "document.pdf", size = Some(100L), description = None,
		submission = DataSubmission(organization, Instant.parse("2020-01-01T00:00:00Z"), None),
		previousVersion = None, nextVersion = None, latestVersion = Left(objectUri),
		parentCollections = Nil, references = References.empty.copy(title = Some("Test document"))
	)
	private val collection = StaticCollection(
		res = collectionUri, hash = hash,
		members = Seq(PlainStaticObject(objectUri, hash, document.fileName)),
		creator = organization, title = "Test collection", description = None,
		previousVersion = None, nextVersion = None, latestVersion = Left(collectionUri),
		parentCollections = Nil, doi = None, coverage = None, documentation = None,
		references = References.empty
	)

	private def fetcher(doc: DocObject): UriSerializer = new UriSerializer {
		def marshaller: ToResponseMarshaller[Uri] = fail("Unexpected marshalling")
		def fetchStaticObject(uri: Uri): Validated[StaticObject] = {
			assert(uri.toString == objectUri.toString)
			Validated.ok(doc)
		}
		def fetchStaticCollection(uri: Uri): Validated[StaticCollection] = {
			assert(uri.toString == collectionUri.toString)
			Validated.ok(collection)
		}
		def fetchCustomLandingPage(uri: Uri): Validated[Option[URI]] = fail("Unexpected landing page lookup")
	}

	/** Accepts registrations locally so removing the guard produces a real, observable PUT. */
	private def withDataCite(test: (DoiConfig, ConcurrentLinkedQueue[String]) => Unit): Unit = {
		val requests = new ConcurrentLinkedQueue[String]()
		val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
		server.createContext("/dois/", exchange => {
			try {
				requests.add(new String(exchange.getRequestBody.readAllBytes(), UTF_8))
				exchange.sendResponseHeaders(201, -1)
			} finally exchange.close()
		})
		server.start()
		try {
			val config = DoiConfig(
				URI(s"http://127.0.0.1:${server.getAddress.getPort}/"),
				Map(Envri.ICOS -> DoiMemberConfig("test", "test", "10.12345"))
			)
			test(config, requests)
		} finally server.stop(0)
	}

	for ((label, authors) <- Seq("missing" -> None, "empty" -> Some(Seq.empty[Agent]))) {
		it(s"rejects a document with $label authors before registering a draft DOI") {
			withDataCite { (config, requests) =>
				val doc = document.copy(references = document.references.copy(authors = authors))
				val result = Await.result(DoiService(config, fetcher(doc)).createDraftDoi(objectUri), 10.seconds)
				assert(requests.isEmpty, "No metadata should be sent to DataCite without creators")
				assert(result.result.isEmpty)
				assert(result.errors.contains("Refusing to create a DOI without authors (creators)"))
			}
		}
	}

	it("rejects a collection whose members have no authors, even though the collection has a creator") {
		withDataCite { (config, requests) =>
			val result = Await.result(DoiService(config, fetcher(document)).createDraftDoi(collectionUri), 10.seconds)
			assert(requests.isEmpty, "No metadata should be sent to DataCite without creators")
			assert(result.result.isEmpty)
			assert(result.errors.contains("Refusing to create a DOI without authors (creators)"))
		}
	}

	it("still registers a document with authors and includes them as DOI creators") {
		withDataCite { (config, requests) =>
			val doc = document.copy(references = document.references.copy(authors = Some(Seq(organization))))
			val result = Await.result(DoiService(config, fetcher(doc)).createDraftDoi(objectUri), 10.seconds)
			assert(result.result.isDefined)
			assert(result.errors.isEmpty)
			val payloads = requests.asScala.toSeq
			assert(payloads.size == 1)
			val attributes = payloads.head.parseJson.asJsObject.fields("data").asJsObject.fields("attributes").asJsObject
			val creators = attributes.fields("creators").asInstanceOf[JsArray].elements
			assert(creators.size == 1)
			assert(creators.head.asJsObject.fields("name") == JsString(organization.name))
			assert(attributes.fields("url") == JsString(objectUri.toString))
		}
	}
}
