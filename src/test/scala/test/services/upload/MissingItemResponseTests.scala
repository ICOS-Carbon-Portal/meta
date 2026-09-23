package se.lu.nateko.cp.meta.test.services.upload

import scala.language.unsafeNulls

import akka.http.scaladsl.model.StatusCodes
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funsuite.AnyFunSuite
import se.lu.nateko.cp.doi.{Doi, DoiMeta}
import se.lu.nateko.cp.meta.HandleNetClientConfig
import se.lu.nateko.cp.meta.api.{HandleNetClient, RdfLens, RdfLenses}
import se.lu.nateko.cp.meta.api.RdfLens.GlobConn
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.instanceserver.Rdf4jInstanceServer
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.services.citation.{CitationMaker, CitationStyle, PlainDoiCiter}
import se.lu.nateko.cp.meta.services.upload.{CollectionReader, PageContentMarshalling, StaticObjectReader}

import scala.util.Try

class MissingItemResponseTests extends AnyFunSuite:

	private def withEmptyStore[T](testBody: Rdf4jInstanceServer => T): T =
		val repo = new SailRepository(new MemoryStore)
		try
			testBody(Rdf4jInstanceServer(repo, "https://meta.icos-cp.eu/tests/missing-items/"))
		finally repo.shutDown()

	test("a nonexistent collection returns HTTP 404 rather than 500 for JSON"):
		withEmptyStore: server =>
			val factory = server.factory
			val reader = new CollectionReader(CpmetaVocab(factory), _ => fail("Missing collections must not be cited"))
			val uri = factory.createIRI("https://meta.icos-cp.eu/collections/old_vJN69j6rRPKxTbJZckEa")

			server.access: conn ?=>
				given GlobConn = RdfLens.global(using conn)
				val collection = reader.fetchStaticColl(uri, None)

				assert(PageContentMarshalling.getJson(collection).status === StatusCodes.NotFound)
				assert(collection.result.isEmpty)
				assert(collection.errors.isEmpty)

	test("a nonexistent data object returns HTTP 404 rather than 500 for JSON"):
		withEmptyStore: server =>
			given Envri = Envri.ICOS
			val coreConf = MetaCoreConfig.default
			given EnvriConfigs = coreConf.envriConfigs
			val factory = server.factory
			val vocab = CpVocab(factory)
			val metaVocab = CpmetaVocab(factory)
			val doiCiter = new PlainDoiCiter:
				def getCitationEager(doi: Doi, style: CitationStyle): Option[Try[String]] =
					fail("Missing objects must not be cited")
				def getDoiEager(doi: Doi): Option[Try[DoiMeta]] =
					fail("Missing objects must not trigger DOI lookups")
			val citer = new CitationMaker(doiCiter, vocab, metaVocab, coreConf)
			val lenses = new RdfLenses(Map.empty, Map.empty, Map.empty, Map.empty, Map.empty)
			val pidFactory = new HandleNetClient.PidFactory(
				HandleNetClientConfig(Map.empty, "https://hdl.handle.net/", None, "", "", dryRun = true)
			)
			val reader = new StaticObjectReader(vocab, metaVocab, lenses, pidFactory, citer)
			val uri = factory.createIRI("https://meta.icos-cp.eu/objects/old_vJN69j6rRPKxTbJZckEa")

			server.access: conn ?=>
				given GlobConn = RdfLens.global(using conn)
				val obj = reader.fetchStaticObject(uri)

				assert(PageContentMarshalling.getJson(obj).status === StatusCodes.NotFound)
				assert(obj.result.isEmpty)
				assert(obj.errors.isEmpty)
