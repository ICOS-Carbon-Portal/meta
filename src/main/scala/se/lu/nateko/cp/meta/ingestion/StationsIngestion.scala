package se.lu.nateko.cp.meta.ingestion

import java.net.{URLEncoder, URI}

import org.openrdf.model.vocabulary.{XMLSchema, RDF}
import org.openrdf.model.{ValueFactory, Statement}


case class BasicStation(
	val classUri: URI,
	val shortName: String,
	val longName: String,
	val stationClass: Int,
	val country: String,
	val elevationAG: Option[String],
	val elevationAS: Option[Float],
	val fundingForConstruction: String,
	val fundingForOperation: String,
	val siteType: String,
	val operationalDateEstimate: String,
	val stationKind: String,
	val isOperational: Boolean,
	val hasPreIcosMeas: Boolean,
	val piName: String,
	val piEmail: String
)

sealed trait Station{
	def basic: BasicStation
}
case class OceanStation(val basic: BasicStation, val location: Either[String, (Double, Double)]) extends Station
case class AtmEcoStation(val basic: BasicStation, val lat: Double, val lon: Double) extends Station

object StationsIngestion extends Ingester{

	def getStationsTable: TextTable = {
		val stationsStream = getClass.getResourceAsStream("/stations.csv")
		new TsvDataTable(stationsStream)
	}

	def rowToStation(row: TextTableRow): Station = {

		def opt(ind: Int): Option[String] = {
			val cell = row(ind)
			if(cell == null || cell.isEmpty) None
			else Some(cell.trim)
		}

		def location: Either[String, (Double, Double)] =
			(dblOpt(4), dblOpt(5)) match{
				case (Some(lat), Some(lon)) =>
					Right((lat, lon))
				case _ =>
					val locDescr: String = Seq(opt(4), opt(5)).flatten.distinct.mkString(",")
					Left(if(locDescr.isEmpty) "Undefined" else locDescr)
			}


		def dbl(ind: Int): Double = {
			row(ind).trim.replace(',', '.').toDouble
		}

		def dblOpt(ind: Int): Option[Double] =
			try{Some(dbl(ind))}
			catch{ case _ => None}

		val basic = BasicStation(
			shortName = row(0),
			longName = row(1),
			classUri = themeToUri(row(2).toLowerCase.trim),
			country = row(3),
			elevationAS = opt(6).map(_.toFloat),
			elevationAG = opt(7),
			stationClass = row(8).toInt,
			siteType = row(9),
			stationKind = row(10),
			piName = row(11),
			piEmail = row(12),
			hasPreIcosMeas = row(13).toUpperCase.trim == "YES",
			operationalDateEstimate = row(14),
			isOperational = row(15).toUpperCase.trim == "X",
			fundingForConstruction = row(16),
			fundingForOperation = row(17)
		)

		if (basic.classUri == os){
			OceanStation(basic, location)
		} else {
			AtmEcoStation(basic, dbl(4), dbl(5))
		}
	}

	def stationToStatements(station: Station, factory: ValueFactory): Seq[Statement] = {
		implicit val f = factory
		def netToSesame(uri: URI) = factory.createURI(uri.toString)
		def plainLit(lit: String) = factory.createLiteral(lit, XMLSchema.STRING)
		def lit(lit: String, dtype: org.openrdf.model.URI) = factory.createLiteral(lit, dtype)

		val basic = station.basic
		
		val statUri = factory.createURI(basic.classUri.toString + "/" + URLEncoder.encode(basic.shortName, "UTF-8"))

		val conditionals = Seq(
			basic.elevationAS.map{elevation =>
				factory.createStatement(statUri, uri("hasElevationAboveSea"), lit(elevation.toString, XMLSchema.FLOAT))
			},
			basic.elevationAG.map{elevation =>
				factory.createStatement(statUri, uri("hasElevationAboveGround"), plainLit(elevation))
			}
		).flatten

		conditionals ++ Seq(
			factory.createStatement(statUri, RDF.TYPE, netToSesame(basic.classUri)),
			factory.createStatement(statUri, uri("hasShortName"), plainLit(basic.shortName)),
			factory.createStatement(statUri, uri("hasLongName"), plainLit(basic.longName)),
			factory.createStatement(statUri, uri("hasCountry"), plainLit(basic.country)),
			//factory.createStatement(, , ),
			//factory.createStatement(, , ),
			//factory.createStatement(, , ),
			//factory.createStatement(, , ),
			//factory.createStatement(, , ),
			//factory.createStatement(, , ),
			//factory.createStatement(, , ),
			//factory.createStatement(, , ),
			//factory.createStatement(, , ),
			//factory.createStatement(, , ),
			//factory.createStatement(, , ),
			//factory.createStatement(, , ),
			//factory.createStatement(, , ),
		)
	}

	def getUri(stationType: String): URI = {
		themeToUri(stationType)
	}

	val prefix = "http://meta.icos-cp.eu/ontologies/stationentry/"

	def uri(fragment: String)(implicit valueFactory: ValueFactory) = valueFactory.createURI(prefix + fragment)

	val es = new URI(prefix + "ES")
	val as = new URI(prefix + "AS")
	val os = new URI(prefix + "OS")

	val themeToUri = Map(
		"eco" -> es,
		"atm" -> as,
		"ocean" -> os,
		"oce" -> os
	)


	override def getStatements(valueFactory: ValueFactory): Iterator[Statement] =
		getStationsTable.rows.iterator
			.flatMap(row => {
				val station = rowToStation(row)
				stationToStatements(station, valueFactory)
			})
}

