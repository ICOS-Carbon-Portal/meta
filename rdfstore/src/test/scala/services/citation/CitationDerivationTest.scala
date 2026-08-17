package se.lu.nateko.cp.meta.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.IRI
import org.scalatest.funspec.AsyncFunSpec
import se.lu.nateko.cp.meta.RdfStoreConfigLoader
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataService
import se.lu.nateko.cp.meta.services.sparql.regression.{CitationClientDummy, TestDb}

import scala.concurrent.Future

/**
 * Guards the lens categories `CitationProvider` actually reads from (task 25, stage 1). The
 * metadata-instance and portal-metadata categories are now built empty; if any rdfStore read path
 * did depend on them after all, the derivations below would stop resolving.
 *
 * Note on ENVRI coverage: lens *resolution* is asserted for every configured ENVRI, but only ICOS
 * has collection and document fixtures in the regression corpus, so fixture-backed derivation is
 * ICOS-only. SITES data objects are covered by the derivation of a discovered object per ENVRI
 * where fixtures allow.
 */
@tags.DbTest
class CitationDerivationTest extends AsyncFunSpec:

	private lazy val conf = RdfStoreConfigLoader.citationStoreConfig

	describe("configured graph scopes"):

		val lenses = CitationProvider.getLenses(conf.citationGraphs)

		val envris = conf.citationGraphs.envris.toSeq.sortBy(_.toString)

		it("covers at least the two supported ENVRIs"):
			Future.successful(assert(envris.toSet === Set(Envri.ICOS, Envri.SITES)))

		envris.foreach: envri =>
			given Envri = envri

			it(s"resolves a collection lens for $envri"):
				Future.successful(assert(lenses.collectionLens.result.isDefined))

			it(s"resolves a document lens for $envri"):
				Future.successful(assert(lenses.documentLens.result.isDefined))

			it(s"resolves a data-object lens for every configured format of $envri"):
				val formats = conf.citationGraphs.dataObjects(envri).definitions.map(_.format)
				Future.successful:
					assert(formats.nonEmpty)
					assert(formats.forall(lenses.dataObjectLens(_).result.isDefined))

		it("has no metadata-instance or portal-metadata lens"):
			Future.successful:
				assert(lenses.metaInstances.isEmpty)
				assert(envris.forall(envri => lenses.metaInstanceLens(using envri).result.isEmpty))

		it("has complete direct graph scopes for every ENVRI"):
			Future.successful(assert(conf.citationGraphs.validated === conf.citationGraphs))

	describe("derived metadata"):

		lazy val db = TestDb()

		lazy val citer =
			given ActorSystem = ActorSystem("CitationDerivationTest")
			new CitationProvider(
				db.repo, _ => CitationClientDummy, conf.core,
				CitationProvider.getLenses(conf.citationGraphs),
				CitationProvider.pidFactory(conf)
			)

		lazy val derived = DerivedMetadataService(citer)

		def anyOfClass(className: String, extraClause: String = ""): Future[IRI] =
			val query = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
				select ?s where{ ?s a cpmeta:$className . $extraClause } order by ?s limit 1"""
			db.runSparql(query).map: rows =>
				val row = rows.toIndexedSeq.headOption
					.getOrElse(fail(s"no cpmeta:$className in the regression fixtures"))
				row.getValue("s").asInstanceOf[IRI]

		def derive(className: String, extraClause: String = "") =
			anyOfClass(className, extraClause).map: iri =>
				val result = derived.resolve(iri)
				assert(result.status === "ready", s"could not derive metadata for $iri")
				iri -> result.metadata.getOrElse(fail(s"no metadata for $iri"))

		def assertCitable(className: String, extraClause: String = ""): Future[org.scalatest.Assertion] =
			derive(className, extraClause).map: (iri, metadata) =>
				assert(
					metadata.references.citationString.isDefined || metadata.citationString.isDefined,
					s"no citation derived for $iri"
				)

		it("derives references and a citation for a data object"):
			assertCitable("DataObject")

		it("derives references and a citation for a document object"):
			assertCitable("DocumentObject")

		// Not every fixture collection carries a DOI, and a collection's citation string comes from
		// its DOI, so plain reference resolution and the citation path are asserted separately.
		it("derives references for a collection"):
			derive("Collection").map: (iri, metadata) =>
				assert(metadata.references.title.isDefined, s"no title derived for $iri")

		it("derives a citation for a collection with a DOI"):
			assertCitable("Collection", "?s cpmeta:hasDoi ?doi .")

		it("derives a licence for a data object"):
			anyOfClass("DataObject").map: iri =>
				assert(citer.getLicence(iri).isDefined, s"no licence derived for $iri")

end CitationDerivationTest
