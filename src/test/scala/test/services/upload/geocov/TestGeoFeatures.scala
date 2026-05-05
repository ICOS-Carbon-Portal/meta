package se.lu.nateko.cp.meta.test.services.upload.geocov

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{HttpRequest, MediaTypes}
import akka.http.scaladsl.unmarshalling.Unmarshal
import se.lu.nateko.cp.cpauth.core.JsonSupport.immSeqFormat
import se.lu.nateko.cp.meta.core.data.JsonSupport.given
import se.lu.nateko.cp.meta.core.data.{DataObject, PlainStaticObject, StaticCollection, GeoFeature, GeoTrack, FeatureCollection, Position, Circle, Polygon}
import se.lu.nateko.cp.meta.utils.async.traverseFut
import spray.json.RootJsonFormat
import spray.json.given

import java.io.{BufferedReader, BufferedWriter, InputStreamReader, OutputStreamWriter}
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.StandardOpenOption.{CREATE, TRUNCATE_EXISTING, WRITE}
import java.nio.file.{Files, Paths}
import java.util.zip.{GZIPInputStream, GZIPOutputStream}
import scala.concurrent.Future
import scala.util.Using

object TestGeoFeatures:

	//private val exampleCollUri = "https://meta.icos-cp.eu/collections/TqzkW2nUzbufj_6pnJzzq7o5" // OTC release
	//private val inputGeosJsonPath = Paths.get("./src/test/resources/GeoCovOceanTestInput.json.gz")
	private val exampleCollUri = "https://meta.icos-cp.eu/collections/rOZ8Ehl3i0VZp8nqJQR2PB3y" //ETC release
	private val inputGeosJsonPath = Paths.get("./src/test/resources/GeoCovTestInput.json.gz")

	def readTestInput(): Seq[GeoFeature] =
		if !Files.exists(inputGeosJsonPath) then
			println("Input not found, fetching and preparing, may take a minute...")
			prepareTestInput()
			Nil
		else
			Using(
				BufferedReader(
					InputStreamReader(
						GZIPInputStream(
							Files.newInputStream(inputGeosJsonPath)
						),
						StandardCharsets.UTF_8
					)
				)
			): reader =>
				import scala.jdk.CollectionConverters.IteratorHasAsScala
				reader.lines().iterator().asScala.mkString("\n").parseJson.convertTo[Seq[GeoFeature]]
			.get
	end readTestInput

	//fetch geo coverage for an example collection, save to a JSON file to be versioned
	def prepareTestInput(): Unit =
		import scala.concurrent.ExecutionContext.Implicits.global
		given sys: ActorSystem = ActorSystem("geo_cov_test_data")

		def getJson[T: RootJsonFormat](url: String): Future[T] = Http()
			.singleRequest(
				HttpRequest(uri = url, headers = Seq(Accept(MediaTypes.`application/json`)))
			)
			.flatMap(Unmarshal(_).to[T])

		getJson[StaticCollection](exampleCollUri)
			.flatMap: coll =>
				traverseFut(
					coll.members.collect:
						case pso: PlainStaticObject => pso.res
					//.take(2)
				): dobjUri =>
					getJson[DataObject](dobjUri.toString)

			.flatMap: dobjs =>
				Future.fromTry:
					Using(
						BufferedWriter(
							OutputStreamWriter(
								GZIPOutputStream(Files.newOutputStream(inputGeosJsonPath, CREATE, WRITE, TRUNCATE_EXISTING)),
								StandardCharsets.UTF_8
							)
						)
					): writer =>
						writer.write:
							dobjs.flatMap(_.coverage).toSeq.toJson.prettyPrint
						println(s"Written compressed json to ${inputGeosJsonPath.toAbsolutePath}")
						akka.Done
			.onComplete: doneTry =>
				sys.terminate()
				doneTry.failed.foreach(_.printStackTrace())

	end prepareTestInput

	// https://meta.icos-cp.eu/collections/QoycFqCewfdEXsTxZMU6XJH9
	val geoFeatures = Vector(
		Position(48.476357,2.780096,Some(103.0f),Some("FR-Fon"),None),
		Circle(Position(48.475868,2.780064,None,None,None),25.2,None,None),
		Circle(Position(48.476095,2.77934,None,None,None),25.2,None,None),
		Circle(Position(48.476536,2.779682,None,None,None),25.2,None,None),
		Circle(Position(48.476225,2.780558,None,None,None),25.2,None,None),
		Polygon(Vector(Position(48.475908,2.785777,None,None,None), 
		Position(48.478101,2.787856,None,None,None),
		Position(48.479848,2.786875,None,None,None), 
		Position(48.480506,2.786456,None,None,None),
		Position(48.480789,2.785678,None,None,None),
		Position(48.480814,2.784824,None,None,None), 
		Position(48.480794,2.783953,None,None,None), 
		Position(48.480944,2.783584,None,None,None), 
		Position(48.480903,2.783067,None,None,None),
		Position(48.480689,2.782683,None,None,None),
		Position(48.477977,2.777622,None,None,None),
		Position(48.476735,2.775239,None,None,None),
		Position(48.47323,2.779432,None,None,None),
		Position(48.474982,2.782723,None,None,None),
		Position(48.473986,2.783961,None,None,None)),Some("TA"),None)
	)

	val oceanGeoTracks = Vector(
		GeoTrack(
			Vector(
				Position(57.438,10.545,None,None,None),
				Position(57.49,11.364,None,None,None),
				Position(56.483,11.959,None,None,None),
				Position(56.036,12.654,None,None,None),
				Position(56.056,10.852,None,None,None),
				Position(56.7,11.913,None,None,None),
				Position(57.669,11.164,None,None,None),
				Position(57.826,9.413,None,None,None),
				Position(57.796,6.668,None,None,None),
				Position(58.388,5.308,None,None,None),
				Position(59.746,-1.746,None,None,None),
				Position(62.0,-6.766,None,None,None),
				Position(63.334,-21.366,None,None,None),
				Position(64.012,-22.995,None,None,None),
				Position(64.188,-22.047,None,None,None),
				Position(61.779,-35.959,None,None,None),
				Position(59.562,-43.881,None,None,None),
				Position(63.864,-52.267,None,None,None),
				Position(63.996,-52.275,None,None,None),
				Position(64.159,-51.726,None,None,None)
			),None,Some(URI("http://meta.icos-cp.eu/resources/spcov_w4oYzE_oBW15PfaVTTolOdqc"))),
		GeoTrack(
			Vector(
				Position(64.123,-51.889,None,None,None),
				Position(63.889,-52.277,None,None,None),
				Position(61.87,-50.167,None,None,None),
				Position(60.092,-48.277,None,None,None),
				Position(59.042,-45.139,None,None,None),
				Position(58.666,-42.009,None,None,None),
				Position(59.12,-40.092,None,None,None),
				Position(60.929,-34.162,None,None,None),
				Position(64.141,-22.857,None,None,None),
				Position(63.511,-22.08,None,None,None),
				Position(63.19,-19.948,None,None,None),
				Position(64.055,-15.074,None,None,None),
				Position(64.9,-13.521,None,None,None),
				Position(62.059,-7.709,None,None,None),
				Position(58.833,0.899,None,None,None),
				Position(57.714,6.822,None,None,None),
				Position(57.685,7.91,None,None,None),
				Position(56.77,11.866,None,None,None),
				Position(56.053,10.835,None,None,None),
				Position(56.158,10.252,None,None,None)
			),None,Some(URI("http://meta.icos-cp.eu/resources/spcov_GhWmNZuocNFf9_Kf8A2OGRrY"))),
		GeoTrack(
			Vector(
				Position(56.115,10.414,None,None,None),
				Position(56.184,12.347,None,None,None),
				Position(56.014,12.667,None,None,None),
				Position(56.201,12.161,None,None,None),
				Position(56.052,10.823,None,None,None),
				Position(56.698,11.91,None,None,None),
				Position(57.679,11.13,None,None,None),
				Position(57.839,10.268,None,None,None),
				Position(57.807,6.649,None,None,None),
				Position(58.712,4.488,None,None,None),
				Position(59.176,3.899,None,None,None),
				Position(60.901,-0.776,None,None,None),
				Position(62.0,-6.766,None,None,None),
				Position(63.317,-21.328,None,None,None),
				Position(64.141,-23.225,None,None,None),
				Position(62.967,-35.093,None,None,None),
				Position(59.102,-42.663,None,None,None),
				Position(59.791,-48.399,None,None,None),
				Position(64.002,-52.265,None,None,None),
				Position(64.167,-51.722,None,None,None)
			),None,Some(URI("http://meta.icos-cp.eu/resources/spcov_13JNLkPUbi75EpLhujK8CtQK")))
	)

	// https://meta.icos-cp.eu/objects/qtCBVnL4CL_GGscXknBHLPFN	
	val modisWithFewerPoints = Vector(
		FeatureCollection(
			Vector(
				Position(-22.283,133.249,None,Some("AU-ASM"),None),
				Position(-13.0769,131.1178,None,Some("AU-Ade"),None),
				Position(-34.0021,140.5891,None,Some("AU-Cpr"),None),
				Position(-33.6152,150.7236,None,Some("AU-Cum"),None),
				Position(-14.0633,131.3181,None,Some("AU-DaP"),None),
				Position(-14.1593,131.3881,None,Some("AU-DaS"),None),
				Position(-15.2588,132.3706,None,Some("AU-Dry"),None),
				Position(-23.8587,148.4746,None,Some("AU-Emr"),None),
				Position(-12.5452,131.3072,None,Some("AU-Fog"),None),
				Position(-30.1913,120.6541,None,Some("AU-GWW"),None),
				Position(-31.3764,115.7138,None,Some("AU-Gin"),None),
				Position(-12.4943,131.1523,None,Some("AU-How"),None),
				Position(-34.4704,140.6551,None,Some("AU-Lox"),None),
				Position(-14.5636,132.4776,None,Some("AU-RDF"),None),
				Position(-36.6499,145.5759,None,Some("AU-Rig"),None),
				Position(-17.1175,145.6301,None,Some("AU-Rob"),None),
				Position(-17.1507,133.3502,None,Some("AU-Stp"),None),
				Position(-22.287,133.64,None,Some("AU-TTE"),None),
				Position(-35.6566,148.1517,None,Some("AU-Tum"),None),
				Position(-37.4259,145.1878,None,Some("AU-Wac"),None),
				Position(-36.6732,145.0294,None,Some("AU-Whr"),None),
				Position(-37.4222,144.0944,None,Some("AU-Wom"),None),
				Position(-34.988282,146.291606,None,Some("AU-Ync"),None),
				Position(-15.4427,167.192001,None,Some("VU-Coc"),None)
			),
			None,
			None
		)
	)

	// https://metalocal.icos-cp.eu/objects/DwQ18YNv6YN4ELts5LwmbQZw
	val modisWithMorePoints = FeatureCollection(
			Vector(
				Position(51.971,4.927,None,Some("NL-Ca1"),None),
				Position(52.003611,4.805556,None,Some("NL-Haa"),None),
				Position(52.24035,5.071301,None,Some("NL-Hor"),None),
				Position(51.953602,4.9029,None,Some("NL-Lan"),None),
				Position(52.166581,5.743556,None,Some("NL-Loo"),None),
				Position(53.398922,6.356028,None,Some("NL-Lut"),None),
				Position(51.65,4.639083,None,Some("NL-Mol"),None),
				Position(78.186,15.923,None,Some("NO-Adv"),None),
				Position(78.921631,11.831085,None,Some("NO-Blv"),None),
				Position(60.372386,11.078142,None,Some("NO-Hur"),None),
				Position(52.762199,16.309401,None,Some("PL-Wet"),None),
				Position(39.138414,-8.332689,None,Some("PT-Cor"),None),
				Position(38.6394,-8.6018,None,Some("PT-Esp"),None),
				Position(38.540639,-8.000056,None,Some("PT-Mi1"),None),
				Position(38.476501,-8.02455,None,Some("PT-Mi2"),None),
				Position(68.362389,18.79475,None,Some("SE-Abi"),None),
				Position(64.182029,19.556539,None,Some("SE-Deg"),None),
				Position(56.265469,13.553514,None,Some("SE-Faj"),None),
				Position(64.112778,19.456944,None,Some("SE-Fla"),None),
				Position(56.09763,13.41897,None,Some("SE-Htm"),None),
				Position(58.34063,13.101768,None,Some("SE-Lnn"),None),
				Position(60.086497,17.479503,None,Some("SE-Nor"),None),
				Position(64.1725,19.738,None,Some("SE-Ros"),None),
				Position(60.125,17.918056,None,Some("SE-Sk1"),None),
				Position(60.129667,17.840056,None,Some("SE-Sk2"),None),
				Position(68.354149,19.050333,None,Some("SE-St1"),None),
				Position(68.356003,19.04521,None,Some("SE-Sto"),None),
				Position(64.25611,19.7745,None,Some("SE-Svb"),None),
				Position(78.186,15.923,None,Some("SJ-Adv"),None),
				Position(78.921631,11.831085,None,Some("SJ-Blv"),None),
				Position(49.120778,20.1635,None,Some("SK-Tat"),None),
				Position(55.7925,-3.24362,None,Some("UK-AMo"),None),
				Position(55.866,-3.205778,None,Some("UK-EBu"),None),
				Position(55.906944,-2.858611,None,Some("UK-ESa"),None),
				Position(56.607222,-3.798056,None,Some("UK-Gri"),None),
				Position(51.153533,-0.8583,None,Some("UK-Ham"),None),
				Position(51.783798,-0.47608,None,Some("UK-Her"),None),
				Position(51.45,-1.266667,None,Some("UK-PL3"),None),
				Position(51.2071,-2.82864,None,Some("UK-Tad"),None)
			),
		None,
		None
	)

end TestGeoFeatures
