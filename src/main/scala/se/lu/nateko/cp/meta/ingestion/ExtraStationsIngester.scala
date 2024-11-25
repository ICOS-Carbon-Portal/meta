package se.lu.nateko.cp.meta.ingestion

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.io.Source

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.ValueFactory

import se.lu.nateko.cp.meta.api.UriId
import se.lu.nateko.cp.meta.core.data.EnvriConfigs
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.*
import se.lu.nateko.cp.meta.api.CloseableIterator
import eu.icoscp.envri.Envri

class ExtraStationsIngester(extraStationsPath: String)(using ExecutionContext, EnvriConfigs) extends Ingester{
	import IcosStationsIngester.*

	def getStatements(vf: ValueFactory): Ingestion.Statements = Future{
		val stationListInput = getClass.getResourceAsStream(extraStationsPath)

		val src = Source.fromInputStream(stationListInput, "UTF-8")
		val iter = src.getLines().drop(1)
			.collect{
				case line if !line.trim.isEmpty => Station.parse(line.trim)
			}
			.flatMap(makeStationStatements(using vf))
		new CloseableIterator.Wrap(iter, src.close)
	}


	private def makeStationStatements(using vf: ValueFactory): Station => Iterator[Statement] = {
		val vocab = new CpVocab(vf)
		val metaVocab = new CpmetaVocab(vf)
		val projToClass = Map(
			Project.INGOS -> metaVocab.ingosStationClass,
			Project.WDCGG -> metaVocab.wdcggStationClass,
			Project.FLUXNET -> metaVocab.fluxnetStationClass,
			Project.ATMO -> metaVocab.atmoDroughtStationClass,
			Project.SAILDRONE -> metaVocab.sailDroneStationClass,
			Project.NEON -> metaVocab.neonStationClass
		)

		station => {
			val stUri = vocab.getStation(UriId(s"${station.project}_${station.id}"))(using Envri.ICOS)

			val iter = projToClass.get(station.project).fold(Iterator.empty[(IRI, IRI, Value)]){stClass =>
				Iterator(
					(stUri, RDF.TYPE, stClass),
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
			}
			iter.map(vf.tripleToStatement)
		}
	}
}


private object IcosStationsIngester{

	enum Project:
		case INGOS, WDCGG, FLUXNET, ATMO, SAILDRONE, NEON

	case class Station(
		project: Project,
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
				Project.valueOf(projStr), id, name, country,
				lat = optDouble(latStr),
				lon = optDouble(lonStr),
				elevation = elevStr.toFloat
			)
		}
	}

	def optDouble(s: String): Option[Double] = if(s.trim.isEmpty) None else Some(s.trim.toDouble)
}
