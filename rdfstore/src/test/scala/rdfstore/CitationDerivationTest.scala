package se.lu.nateko.cp.meta.rdfstore

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.IRI
import org.scalatest.funspec.AsyncFunSpec
import se.lu.nateko.cp.meta.core.data.flattenToSeq
import se.lu.nateko.cp.meta.ConfigLoader
import se.lu.nateko.cp.meta.api.PidFactory
import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.services.external.{ExternalObjFetcher, ExternalProviders}
import se.lu.nateko.cp.meta.test.services.sparql.regression.{CitationClientDummy, TestDb}

import scala.concurrent.Future

@tags.DbTest
class CitationDerivationTest extends AsyncFunSpec{

	private lazy val conf = ConfigLoader.default

	describe("configured graph scopes"){

		val lenses = CitationProviderConfig.getLenses(conf.instanceServers, conf.dataUploadService)

		val envris = (
			conf.dataUploadService.collectionServers.keySet ++
			conf.dataUploadService.documentServers.keySet ++
			conf.instanceServers.forDataObjects.keySet
		).toSeq.sortBy(_.toString)

		it("covers at least the two supported ENVRIs"){
			Future.successful(assert(envris.toSet === Set(Envri.ICOS, Envri.SITES)))
		}

		envris.foreach{envri =>
			given Envri = envri

			it(s"resolves a collection lens for $envri"){
				Future.successful(assert(lenses.collectionLens.result.isDefined))
			}

			it(s"resolves a document lens for $envri"){
				Future.successful(assert(lenses.documentLens.result.isDefined))
			}

			it(s"resolves a data-object lens for every configured format of $envri"){
				val formats = conf.instanceServers.forDataObjects(envri).definitions.map(_.format)
				Future.successful{
					assert(formats.nonEmpty)
					assert(formats.forall(lenses.dataObjectLens(_).result.isDefined))
				}
			}
		}

		it("resolves the configured metadata-instance and portal-metadata lenses"){
			Future.successful{
				assert(lenses.metaInstances.keySet === conf.dataUploadService.metaServers.keySet)
				assert(conf.dataUploadService.metaServers.keys.forall(envri => lenses.metaInstanceLens(using envri).result.isDefined))
				assert(conf.instanceServers.metaFlow.flattenToSeq.forall(flow => lenses.cpLens(flow).result.isDefined))
			}
		}
	}

	describe("derived metadata"){

		lazy val db = TestDb()

		lazy val citer = {
			given ActorSystem = ActorSystem("CitationDerivationTest")
			val upload = conf.dataUploadService
			new CitationProvider(
				db.repo,
				CitationClientDummy,
				conf.core,
				CitationProviderConfig.getLenses(conf.instanceServers, upload),
				new PidFactory(upload.handle.baseUrl, upload.handle.prefix),
				new ExternalObjFetcher(ExternalProviders(upload.externalProviders))
			)
		}

		lazy val derived = DerivedMetadataService(citer)

		def anyOfClass(className: String, extraClause: String = ""): Future[IRI] = {
			val query = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
				select ?s where{ ?s a cpmeta:$className . $extraClause } order by ?s limit 1"""
			db.runSparql(query).map{rows =>
				val row = rows.toIndexedSeq.headOption
					.getOrElse(fail(s"no cpmeta:$className in the regression fixtures"))
				row.getValue("s").asInstanceOf[IRI]
			}
		}

		def derive(className: String, extraClause: String = "") =
			anyOfClass(className, extraClause).map{iri =>
				val result = derived.resolve(iri)
				assert(result.status === "ready", s"could not derive metadata for $iri")
				iri -> result.metadata.getOrElse(fail(s"no metadata for $iri"))
			}

		def assertCitable(className: String, extraClause: String = ""): Future[org.scalatest.Assertion] =
			derive(className, extraClause).map{(iri, metadata) =>
				assert(
					metadata.references.citationString.isDefined || metadata.citationString.isDefined,
					s"no citation derived for $iri"
				)
			}

		it("derives references and a citation for a data object"){
			assertCitable("DataObject")
		}

		it("derives references and a citation for a document object"){
			assertCitable("DocumentObject")
		}

		it("derives references for a collection"){
			derive("Collection").map{(iri, metadata) =>
				assert(metadata.references.title.isDefined, s"no title derived for $iri")
			}
		}

		it("derives a citation for a collection with a DOI"){
			assertCitable("Collection", "?s cpmeta:hasDoi ?doi .")
		}

		it("derives a licence for a data object"){
			anyOfClass("DataObject").map{iri =>
				assert(citer.getLicence(iri).isDefined, s"no licence derived for $iri")
			}
		}
	}

}
