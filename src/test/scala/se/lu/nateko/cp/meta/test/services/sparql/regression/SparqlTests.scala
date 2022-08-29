package se.lu.nateko.cp.meta.test.services.sparql.regression

import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.XSD
import org.eclipse.rdf4j.query.BindingSet
import org.scalatest.BeforeAndAfterAll
import org.scalatest.compatible.Assertion
import org.scalatest.funspec.AsyncFunSpec
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.utils.rdf4j.createLiteral
import org.scalatest
import scala.concurrent.Future
import scala.jdk.CollectionConverters.IterableHasAsScala
import se.lu.nateko.cp.meta.utils.rdf4j.createDateTimeLiteral
import scala.concurrent.ExecutionContext
import org.scalatest.Informer


class QueryTests extends AsyncFunSpec with BeforeAndAfterAll {

	def timedExecution[T](f: Future[T], executedFunction: String, info: Informer)(using ExecutionContext) = {
		val start = System.currentTimeMillis()
		f.onComplete{_ => 
			val end = System.currentTimeMillis()
			info(s"$executedFunction took ${end - start} ms")
		}
		f
	}

	lazy val db = new TestDb

	type Rows = Future[IndexedSeq[BindingSet]]

	override def afterAll() = {
		db.cleanup()
	}

	def describeQfull(
		q: String, descr: String, expectRows: Int, sampleIndex: Int
	)(transformResult: CloseableIterator[BindingSet] => IndexedSeq[BindingSet])(sampleMaker: ValueFactory => Map[String, Value]) =
		describe(descr){
			
			given rows: Rows = for (
				_ <- if(db.repo.isCompleted) db.repo else timedExecution(db.repo, "TestDb init", info);
				r <- timedExecution(db.runSparql(q), descr, info)
			) yield transformResult(r)

			it(s"should return $expectRows rows") {
				rows map { r => {
					assert(r.size === expectRows)
				}}
			}

			it("should return correct sample row") {
				db.repo.flatMap(repo => {
					for (r <- rows) yield {
						val sampleRow = r(sampleIndex).asScala.map(b => b.getName -> b.getValue).toMap
						assert(sampleRow === sampleMaker(repo.getValueFactory()))
					}
				})
			}
		}

	def describeQ(
		q: String, descr: String, expectRows: Int, sampleIndex: Int, sortColumn: String = ""
	)(sampleMaker: ValueFactory => Map[String, Value]) =
		describeQfull(q, descr, expectRows, sampleIndex){
			if (sortColumn.isEmpty) _.toIndexedSeq
			else _.toIndexedSeq.sortBy(_.getValue(sortColumn).stringValue)
		}(sampleMaker)

	describeQ(TestQueries.dataTypeBasics, "Data type basics", expectRows = 76, sampleIndex = 0, sortColumn = "spec") {
		f => Map( 
				"level" -> f.createLiteral("3", XSD.INTEGER),
				"format" -> f.createIRI("http://meta.icos-cp.eu/ontologies/cpmeta/netcdf"),
				"project" -> f.createIRI("http://meta.icos-cp.eu/resources/projects/icos"),
				"type" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/anthropogenicEmissionModelResults"),
				"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/anthropogenicEmissionModelResults"),
				"theme" -> f.createIRI("http://meta.icos-cp.eu/resources/themes/atmosphere"),
				"dataset" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/anthropogenicEmissionModelResultsDataset")
			)
	}

	describeQ(TestQueries.variables, "Variable metadata and relation to data types", expectRows = 457, sampleIndex = 10, sortColumn = "spec") {
		f => Map(
				"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcC14L2DataObject"),
				"variable" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/timeStampColumn"),
				"varTitle" -> f.createLiteral("TIMESTAMP"),
				"valType" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/timeStamp"),
				"quantityUnit" -> f.createLiteral("(not applicable)")
			)
	}

	describeQ(TestQueries.dataObjOriginStats, "Statistics of data object origins", expectRows = 797, sampleIndex = 100, sortColumn = "spec") {
		f => Map(
				"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcCo2L2DataObject"),
				"countryCode" -> f.createLiteral("DE"),
				"submitter" -> f.createIRI("http://meta.icos-cp.eu/resources/organizations/ATC"),
				"count" -> f.createLiteral("2", XSD.INT),
				"station" -> f.createIRI("http://meta.icos-cp.eu/resources/stations/AS_SSL"),
				"stationclass" -> f.createLiteral("ICOS")
			)
	}

	describeQ(TestQueries.detailedDataObjInfo, "Data object details", expectRows = 20, sampleIndex = 10, sortColumn = "dobj") {
		f => Map(
				"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/bfgU0FHcHBw_te8v4J7WSswp"),
				"dois" -> f.createLiteral("")
			)
	}

	describeQ(TestQueries.labels, "Labels for metadata entities", expectRows = 1078, sampleIndex = 500, sortColumn = "uri") {
		f => Map(
				"label" -> f.createLiteral("ocean"), 
				"uri" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/ocean")
			)
	}

	describeQ(TestQueries.keywords, "Keywords associated with data types", expectRows = 94, sampleIndex = 50, sortColumn = "spec") {
		f => Map(
				"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/etcL2Fluxes"),
				"keywords" -> f.createLiteral("ICOS")
			)
	}

	describeQ(TestQueries.stationPositions, "Station positions", expectRows = 196, sampleIndex = 50, sortColumn = "station") {
		f => Map(
				"station" -> f.createIRI("http://meta.icos-cp.eu/resources/stations/ATMO_HUN"),
				"lat" -> f.createLiteral("46.95", XSD.DOUBLE),
				"lon" -> f.createLiteral("16.65", XSD.DOUBLE)
			)
	}

	describeQ(TestQueries.provisionalStationMetadata, "Provisional station metadata", expectRows = 149, sampleIndex = 100, sortColumn = "Id") {
		f => Map(
				"PI_names" -> f.createLiteral("Cremonese"), 
				"Name" -> f.createLiteral("Torgnon"), 
				"Country" -> f.createLiteral("IT"), 
				"Station_class" -> f.createLiteral("Ass"), 
				"themeShort" -> f.createLiteral("ES"), 
				"owlClass" -> f.createIRI("http://meta.icos-cp.eu/ontologies/stationentry/ES"), 
				"lat" -> f.createLiteral("45.84444444", XSD.DOUBLE),
				"Id" -> f.createLiteral("IT-Tor"),
				"s" -> f.createIRI("http://meta.icos-cp.eu/ontologies/stationentry/ES/IT-Tor"), 
				"Elevation_above_sea" -> f.createLiteral("2168.0", XSD.FLOAT), 
				"lon" -> f.createLiteral("7.578055556", XSD.DOUBLE), 
				"Site_type" -> f.createLiteral("Grassland")
			)
	}

	describeQ(TestQueries.productionStationMetadata, "Production station metadata", expectRows = 134, sampleIndex = 100, sortColumn = "Id") {
		f => Map(
				"PI_names" -> f.createLiteral("Lindauer;Müller-Williams;Kneuer"), 
				"prodUri" -> f.createIRI("http://meta.icos-cp.eu/resources/stations/AS_LIN"), 
				"Name" -> f.createLiteral("Lindenberg"), 
				"Country" -> f.createLiteral("DE"), 
				"Elevation_above_sea" -> f.createLiteral("73.0", XSD.FLOAT), 
				"Station_class" -> f.createLiteral("1"), 
				"ps" -> f.createIRI("http://meta.icos-cp.eu/resources/stations/AS_LIN"), 
				"lon" -> f.createLiteral("14.1226", XSD.DOUBLE), 
				"lat" -> f.createLiteral("52.1663", XSD.DOUBLE), 
				"Id" -> f.createLiteral("LIN"), 
				"s" -> f.createIRI("http://meta.icos-cp.eu/ontologies/stationentry/AS/LIN"),
				"Labeling_date" -> f.createLiteral("2018-11-30", XSD.DATE)
			)
	}

	describeQ(TestQueries.atmosphericCO2Level2, "Atmospheric CO2 for level 2 data objects", expectRows = 89, sampleIndex = 40, sortColumn = "spec") {
		f => Map(
				"timeEnd" -> f.createLiteral("2022-02-28T23:00:00Z", XSD.DATETIME),
				"fileName" -> f.createLiteral("ICOS_ATC_L2_L2-2022.1_OPE_10.0_CTS_CO2.zip"), 
				"timeStart" -> f.createLiteral("2016-08-18T00:00:00Z", XSD.DATETIME), 
				"size" -> f.createLiteral("955627", XSD.LONG), 
				"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/R5U1rVcbEQbdf9l801lvDUSZ"), 
				"submTime" -> f.createLiteral("2022-07-08T08:38:35.432Z", XSD.DATETIME), 
				"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcCo2L2DataObject")
			)
	}

	describeQ(TestQueries.objectSpec("atcCh4L2DataObject", "Birkenes"), "Digital objects sorted by station and sampling height based on data specification", expectRows = 3, sampleIndex = 1) {
		f => Map(
				"station" -> f.createLiteral("Birkenes"), "samplingHeight" -> f.createLiteral("50.0", XSD.FLOAT), "dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/6xIDkfjm3iGOM8E_bQAOMx1v")
			)
	}

	describeQ(TestQueries.collections, "All collections", expectRows = 147, sampleIndex = 100) {
		f => Map(
				"description" -> f.createLiteral("ICOS Atmospheric Greenhouse Gas Mole Fractions of CO2, CH4, CO, 14C, N2O, and Meteorological Observations, period up to March 2022, station Ispra, final quality controlled Level 2 data, release 2022-1"), 
				"collection" -> f.createIRI("https://meta.icos-cp.eu/collections/Rd_CaKzAKWC__Rm7Xf71t_uA"), 
				"title" -> f.createLiteral("ICOS Atmosphere Level 2 data, Ispra, release 2022-1"), 
				"doi" -> f.createLiteral("10.18160/NK3J-B45F")
			)
	}

	describeQ(TestQueries.collectionItems("<https://meta.icos-cp.eu/collections/Rd_CaKzAKWC__Rm7Xf71t_uA>"), "All items in specified collection", expectRows = 12, sampleIndex = 10, sortColumn = "dobj") {
		f => Map(
				"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/jgsGu6Zd75iKSwP33_i8sNFC")
			)
	}

	describeQ(TestQueries.stationData("<http://meta.icos-cp.eu/resources/stations/AS_BIR>", 1), "Data objects for specified station", expectRows = 12, sampleIndex = 6, sortColumn = "dobj") {
		f => Map(
				"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/LPF38yK-uZwnzecH-NqfH570"), 
				"timeEnd" -> f.createLiteral("2022-08-10T23:00:00Z", XSD.DATETIME), 
				"specLabel" -> f.createLiteral("ICOS ATC NRT Meteo growing time series"), 
				"bytes" -> f.createLiteral("81530", XSD.LONG), 
				"samplingheight" -> f.createLiteral("50.0", XSD.FLOAT), 
				"datalevel" -> f.createLiteral("1", XSD.INTEGER), 
				"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcMeteoGrowingNrtDataObject"), 
				"timeStart" -> f.createLiteral("2022-03-01T00:00:00Z", XSD.DATETIME), 
				"station" -> f.createIRI("http://meta.icos-cp.eu/resources/stations/AS_BIR")
			)
	}

	describeQ(TestQueries.stations("BIR"), "All known stations including non ICOS stations", expectRows = 2, sampleIndex = 0, sortColumn = "name") {
		f => Map(
				"name" -> f.createLiteral("Birkenes"), 
				"uri" -> f.createIRI("http://meta.icos-cp.eu/resources/stations/AS_BIR"), 
				"country" -> f.createLiteral("NO"), 
				"id" -> f.createLiteral("BIR"), 
				"elevation" -> f.createLiteral("219.0", XSD.FLOAT), 
				"lon" -> f.createLiteral("8.2519", XSD.DOUBLE), 
				"lat" -> f.createLiteral("58.3886", XSD.DOUBLE)
			)
	}

	describeQ(TestQueries.dataObjStation("<https://meta.icos-cp.eu/objects/jgsGu6Zd75iKSwP33_i8sNFC>"), "Information about the station where the data object was sampled", expectRows = 1, sampleIndex = 0, sortColumn = "id") {
		f => Map(
				"samplingHeight" -> f.createLiteral("100.0", XSD.FLOAT), 
				"stationId" -> f.createLiteral("IPR"), 
				"stationName" -> f.createLiteral("Ispra"), 
				"longitude" -> f.createLiteral("8.636", XSD.DOUBLE), 
				"elevation" -> f.createLiteral("210.0", XSD.FLOAT), 
				"theme" -> f.createLiteral("Atmospheric data"), 
				"latitude" -> f.createLiteral("45.8147", XSD.DOUBLE), 
				"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/jgsGu6Zd75iKSwP33_i8sNFC")
			)
	}

	describeQ(TestQueries.ATCStations, "Coordinates of atmospheric ICOS stations", expectRows = 32, sampleIndex = 0) {
		f => Map(
				"PI_names" -> f.createLiteral("Hermansen"), 
				"stationId" -> f.createLiteral("BIR"), 
				"stationName" -> f.createLiteral("Birkenes"), 
				"Country" -> f.createLiteral("NO"), 
				"lon" -> f.createLiteral("8.2519", XSD.DOUBLE), 
				"lat" -> f.createLiteral("58.3886", XSD.DOUBLE), 
				"station" -> f.createIRI("http://meta.icos-cp.eu/resources/stations/AS_BIR")
			)
	}

	describeQ(TestQueries.ATCStationsLevel1Data, "Station names of all stations with atmospheric level 1 data", expectRows = 251, sampleIndex = 0) {
		f => Map("stationName" -> f.createLiteral("Birkenes"), 
			"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/uKY1Fgwz2FPwhDDNKPGrYdxO"), 
			"timeEnd" -> f.createLiteral("2022-08-10T23:00:00Z", XSD.DATETIME), 
			"height" -> f.createLiteral("10.0", XSD.FLOAT),
			"fileName" -> f.createLiteral("ICOS_ATC_NRT_BIR_2022-03-01_2022-08-10_10.0_547_CH4.zip"),
			"variable" -> f.createLiteral("CH4 mixing ratio (dry mole fraction)"), 
			"timeStart" -> f.createLiteral("2022-03-01T00:00:00Z", XSD.DATETIME)
			)
	}

	describeQ(TestQueries.ATCStationList("<http://meta.icos-cp.eu/resources/stations/AS_BIR>", "Ch4", "NrtGrowingDataObject"), "Atmospheric data objects for specified station", expectRows = 3, sampleIndex = 1, sortColumn = "spec") {
		f => Map(
				"timeEnd" -> f.createLiteral("2022-08-10T23:00:00Z", XSD.DATETIME), 
				"fileName" -> f.createLiteral("ICOS_ATC_NRT_BIR_2022-03-01_2022-08-10_50.0_547_CH4.zip"), 
				"timeStart" -> f.createLiteral("2022-03-01T00:00:00Z", XSD.DATETIME), 
				"size" -> f.createLiteral("52584", XSD.LONG), 
				"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/AuW_XXhpYP9I3JChs-KdjZAW"), 
				"submTime" -> f.createLiteral("2022-08-11T10:07:31.796Z", XSD.DATETIME), 
				"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcCh4NrtGrowingDataObject")
			)	
	}

	describeQ(TestQueries.drought2018AtmoProductFileInfo("AS_BIR"), "File info for specified station from the drought2018AtmoProduct", expectRows = 6, sampleIndex = 0) {
		f => Map(
				"samplingHeight" -> f.createLiteral("75.0", XSD.FLOAT), 
				"timeEnd" -> f.createLiteral("2022-08-10T23:00:00Z", XSD.DATETIME), 
				"fileName" -> f.createLiteral("ICOS_ATC_NRT_BIR_2022-03-01_2022-08-10_75.0_547_CO2.zip"), 
				"timeStart" -> f.createLiteral("2022-03-01T00:00:00Z", XSD.DATETIME), 
				"size" -> f.createLiteral("54145", XSD.LONG), 
				"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/2m-Tsf7Q8f9Dhqw4nsHh3ECg"), 
				"submTime" -> f.createLiteral("2022-08-11T10:07:44.885Z", XSD.DATETIME), 
				"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcCo2NrtGrowingDataObject")
			)
	}

	describeQ(TestQueries.drought2018AtmoProductStations, "Metadata about all stations included in the drought2018AtmoProduct", expectRows = 96, sampleIndex = 50, sortColumn = "Long_name") {
		f => Map(
			"height" ->  f.createLiteral("125.0", XSD.FLOAT), 
			"Short_name" -> f.createLiteral("KRE"), 
			"lon" -> f.createLiteral("15.08", XSD.DOUBLE), 
			"lat" -> f.createLiteral("49.572", XSD.DOUBLE), 
			"Long_name" -> f.createLiteral("Křešín u Pacova"), 
			"Country" -> f.createLiteral("CZ")
			)
	}

	describeQ(TestQueries.icosCitation("<https://meta.icos-cp.eu/objects/FCZAo0M_gnyN0RZ4I1J6llzM>"), "Citation of specified data object", expectRows = 1, sampleIndex = 0, sortColumn = "cit") {
		f => Map(
				"cit" -> f.createLiteral("Kubistin, D., Plaß-Dülmer, C., Lindauer, M., Schumacher, M., ICOS RI, 2018. ICOS ATC CO2 Release, Hohenpeissenberg (50.0 m), 2017-02-15–2017-12-31, https://hdl.handle.net/11676/FCZAo0M_gnyN0RZ4I1J6llzM")
			)
	}

	describeQ(TestQueries.prodsPerDomain("atmosphere"), "Level 1 and 2 data product names and specifications for selected domain", expectRows = 14, sampleIndex = 5) {
		f => Map(
				"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcN2oL2DataObject"), "specLabel" -> f.createLiteral("ICOS ATC N2O Release")
			)
	}

	describeQ(TestQueries.prodAvailability("<http://meta.icos-cp.eu/resources/cpmeta/atcCo2NrtGrowingDataObject>"), "Metadata of stations that produce the selected data products", expectRows = 92, sampleIndex = 50, sortColumn = "dobj") {
		f => Map(
				"samplingHeight" -> f.createLiteral("93.0", XSD.FLOAT), 
				"stationId" -> f.createLiteral("HPB"), 
				"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/Z-hMhlZ8BSBK2ZF5zi6wsLHP"), 
				"timeEnd" -> f.createLiteral("2022-08-10T23:00:00Z", XSD.DATETIME), 
				"specLabel" -> f.createLiteral("ICOS ATC NRT CO2 growing time series"), 
				"timeStart" -> f.createLiteral("2022-03-01T00:00:00Z", XSD.DATETIME)
			)
	}

	describeQ(TestQueries.ecosystemRawDataQueryForETC, "ETC raw data search", expectRows = 8, sampleIndex = 6){
		f => Map(
			"pid" -> f.createLiteral("bbigN9036ox4sfLT-KaOAXhY"),
			"fileName" -> f.createLiteral("DE-HoH_BM_20220713_L15_F01.bin")
		)
	}

	describeQ(TestQueries.previewSchemaInfo, "Data object preview schema info", expectRows = 5, sampleIndex = 0) {
		f => Map(
			"valueType" -> f.createLiteral("quality flag"), 
			"valFormat" -> f.createIRI("http://meta.icos-cp.eu/ontologies/cpmeta/bmpChar"), 
			"colName" -> f.createLiteral("Flag"), 
			"objFormat" -> f.createIRI("http://meta.icos-cp.eu/ontologies/cpmeta/asciiAtcProductTimeSer"), 
			"goodFlags" -> f.createLiteral("O;R;U"), 
			"colTip" -> f.createLiteral("single-letter quality flag")
		)
	}

	describeQ(TestQueries.listKnownDataObjects("<https://meta.icos-cp.eu/objects/u9dwttNxsKTrHdQrMCpB18Rb>"), "Known data objects", expectRows = 1, sampleIndex = 0, sortColumn = "dobj") {
		f => Map(
			"hasNextVersion" -> f.createLiteral("false", XSD.BOOLEAN), 
			"timeEnd" -> f.createLiteral("2022-02-28T23:00:00Z", XSD.DATETIME), 
			"fileName" -> f.createLiteral("ICOS_ATC_L2_L2-2022.1_TRN_100.0_CTS_CH4.zip"), 
			"timeStart" -> f.createLiteral("2016-08-11T00:00:00Z", XSD.DATETIME), 
			"size" -> f.createLiteral("902803", XSD.LONG), 
			"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/u9dwttNxsKTrHdQrMCpB18Rb"), 
			"submTime" -> f.createLiteral("2022-07-11T10:33:13.067086Z", XSD.DATETIME), 
			"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcCh4L2DataObject")
		)
	}

	describeQ(TestQueries.previewTableInfo("<https://meta.icos-cp.eu/objects/u9dwttNxsKTrHdQrMCpB18Rb>"), "Preview columns", expectRows = 1, sampleIndex = 0, sortColumn = "dobj") {
		f => Map(
			"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/u9dwttNxsKTrHdQrMCpB18Rb"), 
			"fileName" -> f.createLiteral("ICOS_ATC_L2_L2-2022.1_TRN_100.0_CTS_CH4.zip"), 
			"objSpec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcCh4L2DataObject"), 
			"specLabel" -> f.createLiteral("ICOS ATC CH4 Release"), 
			"startedAtTime" -> f.createLiteral("2016-08-11T00:00:00Z", XSD.DATETIME), 
			"nRows" -> f.createLiteral("48672", XSD.INTEGER)
		)
	}
	
	describeQ(TestQueries.stationLabelingList, "Labeling status for stations", expectRows = 200, sampleIndex = 100, sortColumn = "owlClass") {
		f => Map(
			"s" -> f.createIRI("http://meta.icos-cp.eu/ontologies/stationentry/ES/FR-MON"), 
			"provLongName" -> f.createLiteral("Estrees-Mons A28"), 
			"provShortName" -> f.createLiteral("FR-EM2"), 
			"owlClass" -> f.createIRI("http://meta.icos-cp.eu/ontologies/stationentry/ES"), 
			"hasApplicationStatus" -> f.createLiteral("STEP3APPROVED"), 
			"pi" -> f.createIRI("http://meta.icos-cp.eu/resources/stationentry/Joel_Leonard"), 
			"email" -> f.createLiteral("Joel.Leonard@inra.fr"), 
			"hasAppStatusDate" -> f.createLiteral("2018-11-30T12:00:00Z", XSD.DATETIME)
		)
	}

	describeQ(TestQueries.stationLabelingInfo("<http://meta.icos-cp.eu/ontologies/stationentry/AS/ATM-PAL>"), "Station labeling info", expectRows = 39, sampleIndex = 20, sortColumn = "g") {
		f => Map(
			"p" -> f.createIRI("http://meta.icos-cp.eu/ontologies/stationentry/hasLon"), "g" -> f.createIRI("http://meta.icos-cp.eu/resources/stationlabeling/"), "o" -> f.createLiteral("24.1157", XSD.DOUBLE)
		)
	}

	describeQ(TestQueries.stationLabelingFiles("<http://meta.icos-cp.eu/ontologies/stationentry/AS/ATM-PAL>"), "Uploaded files for station labeling", expectRows = 3, sampleIndex = 1, sortColumn = "file") {
		f => Map(
			"fileName" -> f.createLiteral("Pallas_Sammaltunturi_WindRose_2006-2015.png"), 
			"file" -> f.createIRI("http://meta.icos-cp.eu/files/C5Rx4hcFYCZBDN4c0c4ANS4G"), 
			"fileType" -> f.createLiteral("Prevailing wind directions (closest meteo station)")
		)
	}

	describeQ(TestQueries.dataProdObjectSpec, "Data product specification", expectRows = 79, sampleIndex = 78) {
		f => Map(
			"samplingHeight" -> f.createLiteral("3.0", XSD.FLOAT), 
			"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/wrbjAFaMd4vzkZqUeoquTOfE"), 
			"end" -> f.createLiteral("2022-08-10T23:00:00Z", XSD.DATETIME), 
			"start" -> f.createLiteral("2022-03-01T00:00:00Z", XSD.DATETIME), 
			"station" -> f.createLiteral("Zugspitze")
		)
	}

	describeQ(TestQueries.dashboardDobjList, "Dashboard data object list", expectRows = 1, sampleIndex = 0) {
		f => Map(
			"dataEnd" -> f.createLiteral("2022-08-10T23:00:00Z", XSD.DATETIME), 
			"station" -> f.createIRI("http://meta.icos-cp.eu/resources/stations/AS_SVB"), 
			"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/zj_MnzL-OFmp7k5Jy39an-ZC")
		)
	}

	describeQ(TestQueries.dashboardTableInfo("<https://meta.icos-cp.eu/objects/u9dwttNxsKTrHdQrMCpB18Rb>"), "Dashboard table info", expectRows = 1, sampleIndex = 0, sortColumn = "spec") {
		f => Map(
			"dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/u9dwttNxsKTrHdQrMCpB18Rb"), 
			"fileName" -> f.createLiteral("ICOS_ATC_L2_L2-2022.1_TRN_100.0_CTS_CH4.zip"), 
			"objSpec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcCh4L2DataObject"), 
			"specLabel" -> f.createLiteral("ICOS ATC CH4 Release"), 
			"nRows" -> f.createLiteral("48672", XSD.INTEGER)
		)
	}

	describeQ(TestQueries.findable_L2_L3_specs, "Findable L1 & L2 data object specs", 42, 5, "spec"){
		f => Map("spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcMtoL2DataObject"))
	}

	describeQ(TestQueries.sameFilenameDataObjects("ICOS_ATC_L2_L2-2022.1_BIR_75.0_CTS_MTO.zip"), "By-filename search", 2, 0){
		f => Map("dobj" -> f.createIRI("https://meta.icos-cp.eu/objects/gzsZmFSwlOyPtBmCPzQXU9QY"))
	}

	describeQ(TestQueries.netcdfPreviewAppsOnlyQuery, "NetCDF Preview", 1, 0){
		f => Map(
			"objSpec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/radonFluxSpatialL3"), 
			"specLabel" -> f.createLiteral("Radon flux map")
		)
	}

	describeQ(TestQueries.ecoStationsWhereEmailIsPi, "Stations with matching PI email", 1, 0){
		f => Map("stationId" -> f.createLiteral("DE-Msr"))
	}

	describeQ(TestQueries.licenceSetForDataObjectList, "Licenses for data object list", 1, 0){
		f => Map("lic" -> f.createIRI("http://meta.icos-cp.eu/ontologies/cpmeta/icosLicence"))
	}

	describeQ(TestQueries.ingestionUploadTaskColumnFormats, "IngestionUploadTask column formats", 16, 8, sortColumn = "colName"){
		f => Map(
			"colName" -> f.createLiteral("NEE_UNCLEANED"), 
			"valFormat" -> f.createIRI("http://meta.icos-cp.eu/ontologies/cpmeta/float32")
		)
	}

	describeQ(TestQueries.sitesUploaderStations, "Sites uploader stations", 1, 0){
		f => Map(
			"station" -> f.createIRI("https://meta.fieldsites.se/resources/stations/Svartberget"), 
			"name" -> f.createLiteral("Svartberget Research Station"), 
			"id" -> f.createLiteral("SVB")
		)
	}

	describeQ(TestQueries.svartbergetSites, "Svartberget site", 21, 10){
		f => Map(
			"site" -> f.createIRI("https://meta.fieldsites.se/resources/sites/nyangesbacken-forest"), 
			"name" -> f.createLiteral("Nyängesbäcken - Coniferous forest")
		)
	}

	describeQ(TestQueries.svartbergetForestSamplPoints, "Svartberget forest sample points", 4, 3){
		f => Map(
			"latitude" -> f.createLiteral("64.2322", XSD.DOUBLE), 
			"name" -> f.createLiteral("Svartberget, Åheden AWS"), 
			"point" -> f.createIRI("https://meta.fieldsites.se/resources/position_64.2322_19.7809"), 
			"longitude" -> f.createLiteral("19.7809", XSD.DOUBLE)
		)
	}

	describeQ(TestQueries.specAndDatasetKindInfo, "Spec and dataset kind info", 83, 40){
		f => Map(
			"name" -> f.createLiteral("ICOS ATC NRT CH4 growing time series"), 
			"project" -> f.createIRI("http://meta.icos-cp.eu/resources/projects/icos"), 
			"isSpatioTemp" -> f.createLiteral("false", XSD.BOOLEAN), 
			"projKeywords" -> f.createLiteral("ICOS"), 
			"spec" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcCh4NrtGrowingDataObject"), 
			"theme" -> f.createIRI("http://meta.icos-cp.eu/resources/themes/atmosphere"), 
			"dataLevel" -> f.createLiteral("1", XSD.INTEGER), 
			"keywords" -> f.createLiteral("CH4, concentration"), 
			"dataset" -> f.createIRI("http://meta.icos-cp.eu/resources/cpmeta/atcCh4MoleFracTimeSer")
		)
	}

	describeQ(TestQueries.l3spatialCoverages, "L3 spatial coverages", 5, 2, "label"){
		f => Map(
			"cov" -> f.createIRI("http://meta.icos-cp.eu/resources/latlonboxes/stiltEuropeLatLonBox"), 
			"label" -> f.createLiteral("Europe (STILT)")
		)
	}

	describeQ(TestQueries.datasetVarsInfo, "Dataset vars info", 6, 5, "label"){
		f => Map(
			"label" -> f.createLiteral("rtot"), 
			"title" -> f.createLiteral("rtot"), 
			"unit" -> f.createLiteral("µmol m-2 s-1"), 
			"valueType" -> f.createLiteral("ecosystem respiration")
		)
	}
}
