package se.lu.nateko.cp.meta.test.services.metaexport

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.{RDFS, XSD}
import org.eclipse.rdf4j.model.{IRI, Value}
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.services.metaexport.SchemaOrg
import se.lu.nateko.cp.meta.services.{CpmetaVocab, Rdf4jSparqlRunner}
import se.lu.nateko.cp.meta.services.external.ExternalProviders
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import java.time.Instant

import se.lu.nateko.cp.meta.test.services.external.ExternalTestObjects.*

class SchemaOrgTests extends AnyFunSpec:

	private given Envri = Envri.SITES
	private given EnvriConfig = sitesConf

	private val repo = SailRepository(MemoryStore())
	private val factory = repo.getValueFactory
	private val metaVocab = CpmetaVocab(factory)

	private val spec = factory.createIRI("https://meta.fieldsites.se/resources/objspecs/testSpec")

	private def dobj(name: String): IRI = factory.createIRI(s"https://meta.fieldsites.se/objects/$name")

	private val local = dobj("local")
	private val mirrored = dobj("mirrored")
	private val customLanding = dobj("customLanding")

	private def objTriples(obj: IRI, submitted: Instant, seeAlso: Option[String]): Seq[(IRI, IRI, Value)] =
		val subm = factory.createIRI(obj.stringValue + "/submission")
		Seq(
			(obj, metaVocab.hasObjectSpec, spec),
			(obj, metaVocab.wasSubmittedBy, subm),
			(subm, metaVocab.prov.endedAtTime, factory.createLiteral(submitted.toString, XSD.DATETIME))
		) ++ seeAlso.map(url => (obj, RDFS.SEEALSO, factory.createIRI(url)))

	private val triples =
		Seq[(IRI, IRI, Value)]((spec, metaVocab.hasDataLevel, factory.createLiteral("2", XSD.INTEGER))) ++
		objTriples(local, Instant.parse("2024-01-03T00:00:00Z"), None) ++
		objTriples(mirrored, Instant.parse("2024-01-02T00:00:00Z"), Some("https://meta.icos-cp.eu/objects/abc")) ++
		objTriples(customLanding, Instant.parse("2024-01-01T00:00:00Z"), Some("https://project.example.org/page"))

	repo.transact(conn => triples.foreach((s, p, o) => conn.add(s, p, o))).get

	private val sparqler = Rdf4jSparqlRunner(repo)

	private def sitemapObjs(providers: ExternalProviders): Seq[URI] =
		SchemaOrg.dataObjs(sparqler, None, providers)

	describe("dataObjs"):

		it("excludes objects mirrored from a configured provider"):
			val providers = ExternalProviders(Map(Envri.SITES -> Seq(provider("meta.icos-cp.eu"))))
			assert(sitemapObjs(providers) === Seq(local.toJava, customLanding.toJava))

		it("keeps objects whose landing page belongs to no provider"):
			val providers = ExternalProviders(Map.empty)
			assert(sitemapObjs(providers) === Seq(local.toJava, mirrored.toJava, customLanding.toJava))

		it("only applies the providers of the current ENVRI"):
			val providers = ExternalProviders(Map(Envri.ICOS -> Seq(provider("meta.icos-cp.eu"))))
			assert(sitemapObjs(providers) === Seq(local.toJava, mirrored.toJava, customLanding.toJava))
