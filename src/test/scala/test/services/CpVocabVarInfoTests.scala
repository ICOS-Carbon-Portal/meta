package se.lu.nateko.cp.meta.test.services

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.test.TestConfig.given

import java.net.URI
import scala.language.unsafeNulls

class CpVocabVarInfoTests extends AnyFunSpec:

	given Envri = Envri.ICOS

	describe("CpVocab variable info IRI generation"):
		it("keeps UriId(url segment) as varinfo label token"):
			val repo = new SailRepository(new MemoryStore)
			val vocab = CpVocab(repo.getValueFactory.nn)
			val hash = Sha256Sum.fromBase64Url("old_vJN69j6rRPKxTbJZckEa").get
			val varUri = URI("https://meta.icos-cp.eu/resources/cpmeta/ET_T")

			val varInfoFromUri = vocab.getVarInfo(hash, varUri)
			val varInfoFromString = vocab.getVarInfo(hash, "ET_T")

			assert(varInfoFromUri === varInfoFromString)
			assert(CpVocab.VarInfo.unapply(varInfoFromUri).contains(hash -> "ET_T"))
