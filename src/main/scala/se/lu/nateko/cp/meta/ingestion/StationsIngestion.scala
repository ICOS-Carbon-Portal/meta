package se.lu.nateko.cp.meta.ingestion

import java.net.{URLEncoder, URI}
import org.openrdf.model.vocabulary.{XMLSchema, RDF}
import org.openrdf.model.{ValueFactory, Statement, Literal, Value, URI => SesameURI}
import se.lu.nateko.cp.meta.utils.sesame._
import se.lu.nateko.cp.meta.api.CustomVocab


case class Station(
	val owlClass: String,
	val shortName: String,
	val longName: String,
	val stationClass: String,
	val country: String,
	val location: Either[Option[String], (Double, Double)],
	val elevationAG: Option[String],
	val elevationAS: Option[Float],
	val fundingForConstruction: String,
	val fundingForOperation: String,
	val siteType: String,
	val operationalDateEstimate: Option[String],
	val stationKind: String,
	val isOperational: Boolean,
	val hasPreIcosMeas: Boolean,
	val piName: String,
	val piEmail: String
)

object StationsIngestion extends Ingester{

	val themeToOwlClass = Map(
		"eco" -> "ES",
		"atm" -> "AS",
		"ocean" -> "OS",
		"oce" -> "OS"
	)

	def getStationsTable: TextTable = {
		val stationsStream = getClass.getResourceAsStream("/stations.csv")
		new TrimmingTextTable(new TsvDataTable(stationsStream))
	}

	def rowToStation(row: TextTableRow): Station = {

		def opt(ind: Int): Option[String] = {
			val cell = row(ind)
			if(cell == null || cell.isEmpty) None
			else Some(cell.trim)
		}

		def location: Either[Option[String], (Double, Double)] =
			(dblOpt(4), dblOpt(5)) match{
				case (Some(lat), Some(lon)) =>
					Right((lat, lon))
				case _ =>
					val locDescr: String = Seq(opt(4), opt(5)).flatten.distinct.mkString(", ").trim
					Left(if(locDescr.isEmpty) None else Some(locDescr))
			}


		def dbl(ind: Int): Double = {
			row(ind).trim.replace(',', '.').toDouble
		}

		def dblOpt(ind: Int): Option[Double] =
			try{Some(dbl(ind))}
			catch{ case _ : Throwable => None}

		Station(
			shortName = row(0),
			longName = row(1),
			owlClass = themeToOwlClass(row(2).toLowerCase),
			country = row(3),
			location = location,
			elevationAS = opt(6).map(_.toFloat),
			elevationAG = opt(7),
			stationClass = row(8),
			siteType = row(9),
			stationKind = row(10),
			piName = row(11),
			piEmail = row(12),
			hasPreIcosMeas = row(13).toUpperCase.trim == "YES",
			operationalDateEstimate = opt(14),
			isOperational = row(15).toUpperCase.trim == "X",
			fundingForConstruction = row(16),
			fundingForOperation = row(17)
		)
	}


	def stationToStatements(station: Station, factory: ValueFactory): Seq[Statement] = {
		val vocab = new StationsVocab(factory)
		val classUri = vocab.getRelative(station.owlClass)

		val statUri = factory.createURI(classUri.toString + "/" + URLEncoder.encode(station.shortName, "UTF-8"))

		val conditionals: Seq[(SesameURI, Value)] = Seq(
			station.elevationAS.map{elevation =>
				(vocab.hasElevationAboveSea, vocab.lit(elevation))
			},
			station.elevationAG.map{elevation =>
				(vocab.hasElevationAboveGround, vocab.lit(elevation))
			},
			station.operationalDateEstimate.map{elevation =>
				(vocab.hasOperationalDateEstimate, vocab.lit(elevation))
			}
		).flatten

		val position: Seq[(SesameURI, Value)] = station.location match {
			case Left(Some(pos)) => Seq(
				(vocab.hasLocationDescription, vocab.lit(pos))
			)
			case Left(None) => Nil
			case Right((lat, lon)) => Seq(
				(vocab.hasLat, vocab.lit(lat)),
				(vocab.hasLon, vocab.lit(lon))
			)
		}

		(position ++ conditionals ++ Seq(
			(RDF.TYPE, classUri),
			(vocab.hasShortName, vocab.lit(station.shortName)),
			(vocab.hasLongName, vocab.lit(station.longName)),
			(vocab.hasCountry, vocab.lit(station.country)),
			(vocab.hasStationClass, vocab.lit(station.stationClass)),
			(vocab.hasSiteType, vocab.lit(station.siteType)),
			(vocab.hasStationKind, vocab.lit(station.stationKind)),
			(vocab.hasPiName, vocab.lit(station.piName)),
			(vocab.hasPiEmail, vocab.lit(station.piEmail)),
			(vocab.hasPreIcosMeasurements, vocab.lit(station.hasPreIcosMeas)),
			(vocab.isAlreadyOperational, vocab.lit(station.isOperational)),
			(vocab.hasFundingForConstruction, vocab.lit(station.fundingForConstruction)),
			(vocab.hasFundingForOperation, vocab.lit(station.fundingForOperation))
		)).map{
			case (pred, obj) => factory.createStatement(statUri, pred, obj)
		}
	}

	override def getStatements(valueFactory: ValueFactory): Iterator[Statement] =
		getStationsTable.rows.iterator
			.flatMap(row => {
				val station = rowToStation(row)
				stationToStatements(station, valueFactory)
			})
}

