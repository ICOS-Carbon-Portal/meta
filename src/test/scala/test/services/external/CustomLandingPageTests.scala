package se.lu.nateko.cp.meta.test.services.external

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.stream.Materializer
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS}
import org.eclipse.rdf4j.model.{IRI, Resource, Value}
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.doi.{Doi, DoiMeta}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, EnvriConfigs, staticCollLandingPage, staticObjLandingPage}
import se.lu.nateko.cp.meta.services.citation.{CitationStyle, ExternalObjFetcher, PlainDoiCiter}
import se.lu.nateko.cp.meta.services.linkeddata.Rdf4jUriSerializer
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab, ExternalProviders}
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.{ConfigLoader, MetaDb}

import java.net.URI
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.Try

class CustomLandingPageTests extends AnyFunSpec with BeforeAndAfterAll:

	private val conf = ConfigLoader.default
	private given system: ActorSystem = ActorSystem("CustomLandingPageTests")
	private given Materializer = Materializer(system)
	private given EnvriConfigs = conf.core.envriConfigs
	private given Envri = Envri.SITES
	private given EnvriConfig = summon[EnvriConfigs](Envri.SITES)

	private val repo = SailRepository(MemoryStore())
	private val factory = repo.getValueFactory
	private val vocab = CpVocab(factory)
	private val metaVocab = CpmetaVocab(factory)

	private val zipFormat = factory.createIRI("http://meta.icos-cp.eu/ontologies/cpmeta/zipArchive")
	private val zipGraph = factory.createIRI("https://meta.fieldsites.se/resources/siteszip/")
	private val docGraph = factory.createIRI("https://meta.fieldsites.se/documents/")
	private val spec = factory.createIRI("https://meta.fieldsites.se/resources/objspecs/testSpec")

	private val mirroredHash = Sha256Sum.fromBase64Url("mir_vJN69j6rRPKxTbJZckEa").get
	private val plainHash = Sha256Sum.fromBase64Url("pln_vJN69j6rRPKxTbJZckEa").get
	private val docHash = Sha256Sum.fromBase64Url("doc_vJN69j6rRPKxTbJZckEa").get

	private val icosLandingPage = URI("https://meta.icos-cp.eu/objects/abc")

	private def dataObjTriples(hash: Sha256Sum, seeAlso: Option[URI]): Seq[(Resource, IRI, Value, IRI)] =
		val obj = vocab.getStaticObject(hash)
		Seq(
			(obj, RDF.TYPE, metaVocab.dataObjectClass, zipGraph),
			(obj, metaVocab.hasObjectSpec, spec, zipGraph)
		) ++ seeAlso.map(url => (obj, RDFS.SEEALSO, url.toRdf(using factory), zipGraph))

	private val triples =
		Seq[(Resource, IRI, Value, IRI)]((spec, metaVocab.hasFormat, zipFormat, zipGraph)) ++
		dataObjTriples(mirroredHash, Some(icosLandingPage)) ++
		dataObjTriples(plainHash, None) ++
		Seq(
			(vocab.getStaticObject(docHash), RDF.TYPE, metaVocab.docObjectClass, docGraph),
			(vocab.getStaticObject(docHash), RDFS.SEEALSO, icosLandingPage.toRdf(using factory), docGraph)
		)

	repo.transact(conn => triples.foreach((s, p, o, g) => conn.add(s, p, o, g))).get

	private val doiCiter = new PlainDoiCiter:
		def getCitationEager(doi: Doi, style: CitationStyle): Option[Try[String]] = None
		def getDoiEager(doi: Doi): Option[Try[DoiMeta]] = None

	private val uriSer = new Rdf4jUriSerializer(
		repo,
		vocab,
		metaVocab,
		MetaDb.getLenses(conf.instanceServers, conf.dataUploadService),
		doiCiter,
		ExternalObjFetcher(ExternalProviders(Map.empty)),
		conf
	)

	private def landingPageOf(hash: Sha256Sum) =
		uriSer.fetchCustomLandingPage(Uri(staticObjLandingPage(hash).toString))

	override def afterAll(): Unit =
		Await.ready(system.terminate(), 10.seconds)

	describe("fetchCustomLandingPage"):

		it("reads the landing page of a data object that has not been published"):
			assert(landingPageOf(mirroredHash).result === Some(Some(icosLandingPage)))

		it("is empty for data objects without a custom landing page"):
			assert(landingPageOf(plainHash).result === Some(None))

		it("is empty for document objects"):
			assert(landingPageOf(docHash).result === Some(None))

		it("rejects URIs that are not object landing pages"):
			val collUri = Uri(staticCollLandingPage(plainHash).toString)
			val res = uriSer.fetchCustomLandingPage(collUri)
			assert(res.result === None)
			assert(res.errors === Seq(s"URI $collUri does not have the shape of a data/document object URI"))
