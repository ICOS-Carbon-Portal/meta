package se.lu.nateko.cp.meta.test.services.citation

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.scalatest.funspec.AsyncFunSpec
import se.lu.nateko.cp.meta.services.citation.CitationProvider
import se.lu.nateko.cp.meta.test.TestConfig
import se.lu.nateko.cp.meta.test.services.sparql.regression.{CitationClientDummy, TestDb}

import scala.concurrent.Future

@tags.DbTest
class CitationProviderTests extends AsyncFunSpec {

	lazy val db = TestDb()

	lazy val citer = {
		given ActorSystem = ActorSystem("CitationProviderTests")
		val sail = db.repo.asInstanceOf[SailRepository].getSail
		new CitationProvider(sail, _ => CitationClientDummy, TestConfig.metaConfig)
	}

	def allOfClass(className: String): Future[Seq[IRI]] = {
		val query = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
			select ?s where{ ?s a cpmeta:$className } order by ?s"""
		db.runSparql(query).map(_.toIndexedSeq.map(_.getValue("s").asInstanceOf[IRI]))
	}

	/** The magic SPARQL predicates cpmeta:hasCitationString and dcterms:license, when queried on their own,
	  * are served by CitationProvider.getCitation and getLicence, while cpmeta:hasBiblioInfo and the landing
	  * pages use the citation and licence in the references. These should agree.
	  */
	describe("citation and licence resolves the same citation and licence as the references for every "){
		Seq("DataObject", "DocumentObject", "Collection").foreach{className =>
			it(s"$className"){
				allOfClass(className).map{iris =>
					val mismatches = iris.flatMap{iri =>
						citer.getReferences(iri).toSeq.flatMap{refs =>
							val citation = citer.getCitation(iri).orElse(refs.citationString)
							val licence = citer.getLicence(iri).orElse(refs.licence)
							Option.when(citation != refs.citationString)(
								s"$iri citation: getCitation $citation, references ${refs.citationString}"
							) ++ Option.when(licence != refs.licence)(
								s"$iri licence: getLicence ${licence.map(_.url)}, references ${refs.licence.map(_.url)}"
							)
						}
					}
					assert(iris.nonEmpty, s"no cpmeta:$className in the regression fixtures")
					assert(
						mismatches.isEmpty,
						mismatches.take(20).mkString("\n", "\n", s"\n(${mismatches.size} mismatches in total)")
					)
				}
			}
		}
	}
}
