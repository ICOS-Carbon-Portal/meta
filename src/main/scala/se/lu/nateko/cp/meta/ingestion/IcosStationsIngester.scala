package se.lu.nateko.cp.meta.ingestion

import scala.concurrent.ExecutionContext
import scala.io.Source

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.repository.Repository

import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j._

class IcosStationsIngester(
	sparqlPath: String,
	extraStationsPath: String
)(implicit ctxt: ExecutionContext, envriConfs: EnvriConfigs) extends Extractor{
	import IcosStationsIngester._

	def getStatements(repo: Repository): Ingestion.Statements =
		new SparqlConstructExtractor(sparqlPath).getStatements(repo).map{own =>
			val stationListInput = getClass.getResourceAsStream(extraStationsPath)

			own ++ Source.fromInputStream(stationListInput, "UTF-8")
				.getLines
				.drop(1)
				.collect{
					case line if !line.trim.isEmpty => Station.parse(line.trim)
				}
				.flatMap(makeStationStatements(repo))
		}


	private def makeStationStatements(repo: Repository): Station => Iterator[Statement] = {
		implicit val vf = repo.getValueFactory
		val vocab = new CpVocab(vf)
		val metaVocab = new CpmetaVocab(vf)
		val projToClass = Map(
			Project.INGOS -> metaVocab.ingosStationClass,
			Project.WDCGG -> metaVocab.wdcggStationClass
		)

		station => {
			val stUri = vocab.getTcStation(station.project.toString, station.id)

			Iterator(
				(stUri, RDF.TYPE, projToClass(station.project)),
				(stUri, metaVocab.hasStationId, station.id.toRdf),
				(stUri, metaVocab.hasName, station.name.toRdf),
				(stUri, metaVocab.countryCode, station.country.toRdf),
				(stUri, metaVocab.hasLatitude, vocab.lit(station.lat)),
				(stUri, metaVocab.hasLongitude, vocab.lit(station.lon)),
				(stUri, metaVocab.hasElevation, vocab.lit(station.elevation)),
			).map(vf.tripleToStatement)
		}
	}
}


private object IcosStationsIngester{

	object Project extends Enumeration{
		val INGOS, WDCGG = Value
	}

	case class Station(project: Project.Value, id: String, name: String, country: String, lat: Double, lon: Double, elevation: Double)

	object Station{
		def parse(line: String): Station = {
			val Seq(projStr, id, name, country, latStr, lonStr, elevStr) = line.trim.split('\t').toSeq
			Station(Project.withName(projStr), id, name, country, latStr.toDouble, lonStr.toDouble, elevStr.toDouble)
		}
	}
}
