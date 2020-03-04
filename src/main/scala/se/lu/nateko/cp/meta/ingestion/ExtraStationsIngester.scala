package se.lu.nateko.cp.meta.ingestion

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.io.Source

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.ValueFactory

import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j._

class ExtraStationsIngester(extraStationsPath: String)(implicit ctxt: ExecutionContext, envriConfs: EnvriConfigs) extends Ingester{
	import IcosStationsIngester._

	def getStatements(vf: ValueFactory): Ingestion.Statements = Future{
		val stationListInput = getClass.getResourceAsStream(extraStationsPath)

		Source.fromInputStream(stationListInput, "UTF-8")
			.getLines
			.drop(1)
			.collect{
				case line if !line.trim.isEmpty => Station.parse(line.trim)
			}
			.flatMap(makeStationStatements(vf))
	}


	private def makeStationStatements(implicit vf: ValueFactory): Station => Iterator[Statement] = {
		val vocab = new CpVocab(vf)
		val metaVocab = new CpmetaVocab(vf)
		val projToClass = Map(
			Project.INGOS -> metaVocab.ingosStationClass,
			Project.WDCGG -> metaVocab.wdcggStationClass,
			Project.FLUXNET -> metaVocab.fluxnetStationClass,
			Project.ATMO -> metaVocab.atmoDroughtStationClass,
			Project.SAILDRONE -> metaVocab.sailDroneStationClass
		)

		station => {
			val stUri = vocab.getIcosLikeStation(s"${station.project}_${station.id}")

			val iter = Iterator(
				(stUri, RDF.TYPE, projToClass(station.project)),
				(stUri, metaVocab.hasStationId, station.id.toRdf),
				(stUri, metaVocab.hasName, station.name.toRdf),
				(stUri, metaVocab.countryCode, station.country.toRdf),
				(stUri, metaVocab.hasElevation, vocab.lit(station.elevation)),
			) ++
			station.lat.iterator.map{lat =>
				(stUri, metaVocab.hasLatitude, vocab.lit(lat))
			} ++
			station.lon.iterator.map{lon =>
				(stUri, metaVocab.hasLongitude, vocab.lit(lon))
			}

			iter.map(vf.tripleToStatement)
		}
	}
}


private object IcosStationsIngester{

	object Project extends Enumeration{
		val INGOS, WDCGG, FLUXNET, ATMO, SAILDRONE = Value
	}

	case class Station(
		project: Project.Value,
		id: String,
		name: String,
		country: String,
		lat: Option[Double],
		lon: Option[Double],
		elevation: Float
	)

	object Station{
		def parse(line: String): Station = {
			val Seq(projStr, id, name, country, latStr, lonStr, elevStr) = line.trim.split('\t').toSeq
			Station(
				Project.withName(projStr), id, name, country,
				lat = optDouble(latStr),
				lon = optDouble(lonStr),
				elevation = elevStr.toFloat
			)
		}
	}

	def optDouble(s: String): Option[Double] = if(s.trim.isEmpty) None else Some(s.trim.toDouble)
}
