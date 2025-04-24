package se.lu.nateko.cp.meta.core.data

import scala.language.unsafeNulls

import se.lu.nateko.cp.meta.core.CommonJsonSupport
import se.lu.nateko.cp.meta.core.crypto.JsonSupport.given
import se.lu.nateko.cp.meta.core.toTypedJson
import spray.json.*
import DefaultJsonProtocol.*
import se.lu.nateko.cp.doi.DoiMeta
import CommonJsonSupport.TypeField
import scala.reflect.ClassTag

object JsonSupport extends CommonJsonSupport:
	val GeoJsonField = "coverageGeo"

	given RootJsonFormat[UriResource] = jsonFormat3(UriResource.apply)
	given RootJsonFormat[Project] = jsonFormat2(Project.apply)
	given RootJsonFormat[DataTheme] = jsonFormat3(DataTheme.apply)
	given RootJsonFormat[PlainStaticObject] = jsonFormat3(PlainStaticObject.apply)
	given RootJsonFormat[PlainStaticCollection] = jsonFormat3(PlainStaticCollection.apply)
	given JsonFormat[DatasetType] = enumFormat(DatasetType.valueOf, DatasetType.values)
	given RootJsonFormat[DatasetSpec] = jsonFormat2(DatasetSpec.apply)
	private val vanillaObjectFormatFormat = jsonFormat2(ObjectFormat.apply)
	given RootJsonFormat[ObjectFormat] with
		def read(json: JsValue): ObjectFormat = vanillaObjectFormatFormat.read(json)
		def write(obj: ObjectFormat): JsValue =
			val vanilla = vanillaObjectFormatFormat.write(obj).asJsObject
			// extra field for backwards compatibility with the older version of the JSON
			// (needed by icoscp pylib version 0.1.19 and below)
			vanilla + ("uri" -> obj.self.uri.toJson)

	given RootJsonFormat[DataObjectSpec] = jsonFormat10(DataObjectSpec.apply)

	given RootJsonFormat[Position] = jsonFormat5(Position.apply)
	given RootJsonFormat[LatLonBox] = jsonFormat4(LatLonBox.apply)
	given RootJsonFormat[GeoTrack] = jsonFormat3(GeoTrack.apply)
	given RootJsonFormat[Polygon] = jsonFormat3(Polygon.apply)
	given RootJsonFormat[Circle] = jsonFormat4(Circle.apply)
	given RootJsonFormat[PinKind] = enumFormat(PinKind.valueOf, PinKind.values)
	given RootJsonFormat[Pin] = jsonFormat2(Pin.apply)
	given RootJsonFormat[FeatureWithGeoJson] = jsonFormat2(FeatureWithGeoJson.apply)

	given JsonFormat[CountryCode] with{
		def write(cc: CountryCode): JsValue = JsString(cc.code)
		def read(v: JsValue): CountryCode = v match{
			case JsString(CountryCode(cc)) => cc
			case _ => deserializationError(s"Expected an ISO ALPHA-2 country code string, got ${v.compactPrint}")
		}
	}

	private object vanillaGeoFeatureFormat extends RootJsonFormat[GeoFeature]:

		private def subtypeEntry[T <: GeoFeature : JsonFormat](using tag: ClassTag[T]): (String, JsValue => T) =
			val name = tag.runtimeClass.getSimpleName
			(name, _.convertTo[T])

		private val parsers: Map[String, JsValue => GeoFeature] = Map(
			subtypeEntry[LatLonBox],
			subtypeEntry[GeoTrack],
			subtypeEntry[Position],
			subtypeEntry[Polygon],
			subtypeEntry[FeatureCollection],
			subtypeEntry[Circle],
			subtypeEntry[Pin],
			subtypeEntry[FeatureWithGeoJson]
		)

		def write(geo: GeoFeature): JsValue =
			val vanilla = geo match
				case llb: LatLonBox => llb.toJson
				case gt: GeoTrack => gt.toJson
				case pos: Position => pos.toJson
				case gpoly: Polygon => gpoly.toJson
				case geocol: FeatureCollection => geocol.toJson
				case c: Circle => c.toJson
				case p: Pin => p.toJson
				case jsgf: FeatureWithGeoJson => jsgf.toJson
			vanilla.pluss(TypeField -> geo.getClass.getSimpleName.nn)

		def read(value: JsValue): GeoFeature = value match
			case JsObject(fields) =>
				fields.get(TypeField) match
					case Some(JsString(typeName)) => parsers.get(typeName) match
						case Some(parser) => parser(value)
						case None =>
							val knownTypes = parsers.keys.mkString(", ")
							deserializationError(s"Unexpected GeoFeature type $typeName, supported types are: $knownTypes")
					case _ =>
						deserializationError(s"Expected a $TypeField property representing the type of GeoFeature")
			case _ =>
				deserializationError("Expected a JsObject representing a GeoFeature")
	end vanillaGeoFeatureFormat

	given RootJsonFormat[GeoFeature] with{
		def write(geo: GeoFeature): JsValue =
			val base = vanillaGeoFeatureFormat.write(geo)
			val geoJson = GeoJson.fromFeatureWithLabels(geo)
			base.asJsObject + ("geo" -> geoJson)

		def read(value: JsValue): GeoFeature = vanillaGeoFeatureFormat.read(value)

	}

	given JsonFormat[FeatureCollection] = {
		given JsonFormat[Seq[GeoFeature]] = immSeqFormat(vanillaGeoFeatureFormat)
		jsonFormat3(FeatureCollection.apply)
	}

	given JsonFormat[Orcid] with{
		def write(id: Orcid): JsValue = JsString(id.shortId)

		def read(value: JsValue) = value match{
			case JsString(s) => Orcid.unapply(s) match{
				case Some(o) => o
				case None => deserializationError(s"Could not parse Orcid id from $s")
			}
			case x => deserializationError("Orcid must be represented with a JSON string, got " + x.getClass.getCanonicalName)
		}
	}

	given RootJsonFormat[LinkBox] = jsonFormat4(LinkBox.apply)
	given RootJsonFormat[WebpageElements] = jsonFormat3(WebpageElements.apply)
	given RootJsonFormat[Organization] = jsonFormat5(Organization.apply)
	given RootJsonFormat[InstrumentDeployment] = jsonFormat7(InstrumentDeployment.apply)
	given RootJsonFormat[Instrument] = jsonFormat9(Instrument.apply)
	given RootJsonFormat[Person] = jsonFormat5(Person.apply)
	given RootJsonFormat[Site] = jsonFormat3(Site.apply)
	given JsonFormat[FunderIdType] = enumFormat(FunderIdType.valueOf, FunderIdType.values)
	given RootJsonFormat[Funder] = jsonFormat2(Funder.apply)
	given RootJsonFormat[Funding] = jsonFormat7(Funding.apply)
	given RootJsonFormat[Station] = jsonFormat9(Station.apply)

	given RootJsonFormat[Agent] with{

		def write(agent: Agent): JsValue = agent match{
			case person: Person => person.toJson
			case org: Organization => org.toJson
		}

		def read(value: JsValue): Agent = value match{
			case JsObject(fields) =>
				if(fields.contains("firstName"))
					value.convertTo[Person]
				else
					value.convertTo[Organization]
			case _ =>
				deserializationError("Expected JS object representing eigher a person or an organization")
		}
	}

	given RootJsonFormat[DataProduction] = jsonFormat7(DataProduction.apply)
	given RootJsonFormat[DataAcquisition] = jsonFormat6(DataAcquisition.apply)
	given RootJsonFormat[DataSubmission] = jsonFormat3(DataSubmission.apply)

	given RootJsonFormat[TemporalCoverage] = jsonFormat2(TemporalCoverage.apply)

	given RootJsonFormat[ValueType] = jsonFormat3(ValueType.apply)
	given RootJsonFormat[VarMeta] = jsonFormat7(VarMeta.apply)
	given RootJsonFormat[StationTimeSeriesMeta] = jsonFormat5(StationTimeSeriesMeta.apply)
	given RootJsonFormat[SpatioTemporalMeta] = jsonFormat8(SpatioTemporalMeta.apply)

	given RootJsonFormat[TabularIngestionExtract] = jsonFormat2(TabularIngestionExtract.apply)
	given RootJsonFormat[VarInfo] = jsonFormat3(VarInfo.apply)
	given RootJsonFormat[NetCdfExtract] = jsonFormat1(NetCdfExtract.apply)
	given RootJsonFormat[TimeSeriesExtract] = jsonFormat2(TimeSeriesExtract.apply)
	given RootJsonFormat[SpatialTimeSeriesExtract] = jsonFormat2(SpatialTimeSeriesExtract.apply)

	given RootJsonFormat[IngestionMetadataExtract] with{

		def write(uploadInfo: IngestionMetadataExtract): JsValue = uploadInfo match{
			case ts: TimeSeriesExtract => ts.toJson
			case sts: SpatialTimeSeriesExtract => sts.toJson
			case ncdf: NetCdfExtract => ncdf.toJson
		}

		def read(value: JsValue): IngestionMetadataExtract =  value match {
			case JsObject(fields)  =>
				if(fields.contains("coverage"))
					value.convertTo[SpatialTimeSeriesExtract]
				else if(fields.contains("varInfo"))
					value.convertTo[NetCdfExtract]
				else
					value.convertTo[TimeSeriesExtract]
			case _ =>
				deserializationError("Expected JS object representing upload completion info")
		}
	}

	given RootJsonFormat[UploadCompletionInfo] = jsonFormat2(UploadCompletionInfo.apply)
	given RootJsonFormat[Licence] = jsonFormat4(Licence.apply)
	import se.lu.nateko.cp.doi.core.JsonSupport.{given RootJsonFormat[DoiMeta]}
	given RootJsonFormat[References] = jsonFormat10(References.apply)
	given RootJsonFormat[DocObject] = jsonFormat13(DocObject.apply)

	given RootJsonFormat[DataObject] with {
		private given defFormat: RootJsonFormat[DataObject] = jsonFormat14(DataObject.apply)

		def read(value: JsValue): DataObject = value.convertTo[DataObject](defFormat)

		def write(dobj: DataObject): JsValue =
			val plain = dobj.toJson(defFormat).asJsObject
			dobj.coverage.fold(plain): geo =>
				plain + (GeoJsonField -> GeoJson.fromFeatureWithLabels(geo))
	}

	given RootJsonFormat[StaticObject] with{
		override def read(value: JsValue): StaticObject = value match
			case JsObject(fields)  =>
				if(fields.contains("specification"))
					value.convertTo[DataObject]
				else
					value.convertTo[DocObject]
			case _ =>
				deserializationError("Expected JS object representing a data/doc object")

		override def write(so: StaticObject): JsValue = so match
			case dobj: DataObject => dobj.toJson
			case doc: DocObject => doc.toJson
	}

	given RootJsonFormat[StaticCollection] = jsonFormat14(StaticCollection.apply)

	given RootJsonFormat[PlainStaticItem] with{

		def write(psi: PlainStaticItem): JsValue = psi match
			case pso: PlainStaticObject => pso.toJson
			case psc: PlainStaticCollection =>
				// extra field to prevent breakage of Python using icoscp_core 0.3.5 or older
				psc.toJson.pluss("name" -> psc.name)

		def read(value: JsValue): PlainStaticItem = value match
			case JsObject(fields) =>
				if fields.contains("title") then
					value.convertTo[PlainStaticCollection]
				else
					value.convertTo[PlainStaticObject]
			case _ =>
				deserializationError("Expected JS object representing a plain static collection or a plain data object")
	}

	given JsonFormat[IcosStationClass] = enumFormat(IcosStationClass.valueOf, IcosStationClass.values)
	given RootJsonFormat[AtcStationSpecifics] = jsonFormat7(AtcStationSpecifics.apply)
	given RootJsonFormat[EtcStationSpecifics] = jsonFormat13(EtcStationSpecifics.apply)
	given RootJsonFormat[OtcStationSpecifics] = jsonFormat6(OtcStationSpecifics.apply)
	given RootJsonFormat[SitesStationSpecifics] = jsonFormat8(SitesStationSpecifics.apply)
	given JsonFormat[CityNetwork] with
		def write(cn: CityNetwork): JsValue = JsString(cn)
		def read(js: JsValue): CityNetwork = js match
			case JsString(s) => cityNetworkFromStr(s)
			case _ => "Unspecified"

	given RootJsonFormat[IcosCitiesStationSpecifics] = jsonFormat2(IcosCitiesStationSpecifics.apply)

	private val AtcSpec = "atc"
	private val EtcSpec = "etc"
	private val OtcSpec = "otc"
	private val SitesSpec = "sites"
	private val CitiesSpec = "cities"
	given RootJsonFormat[StationSpecifics] with{
		def write(ss: StationSpecifics): JsValue = ss match{
			case NoStationSpecifics => JsObject.empty
			case atc: AtcStationSpecifics => atc.toTypedJson(AtcSpec)
			case etc: EtcStationSpecifics => etc.toTypedJson(EtcSpec)
			case otc: OtcStationSpecifics => otc.toTypedJson(OtcSpec)
			case sites: SitesStationSpecifics => sites.toTypedJson(SitesSpec)
			case cities: IcosCitiesStationSpecifics => cities.toTypedJson(CitiesSpec)
		}

		def read(value: JsValue) =
			value.asJsObject("StationSpecifics must be a JSON object").fields.get(TypeField) match{
				case Some(JsString(AtcSpec)) => value.convertTo[AtcStationSpecifics]
				case Some(JsString(EtcSpec)) => value.convertTo[EtcStationSpecifics]
				case Some(JsString(OtcSpec)) => value.convertTo[OtcStationSpecifics]
				case Some(JsString(SitesSpec)) => value.convertTo[SitesStationSpecifics]
				case Some(JsString(CitiesSpec)) => value.convertTo[IcosCitiesStationSpecifics]
				case None => NoStationSpecifics
				case Some(unknType) => deserializationError(s"Unknown StationSpecifics type $unknType")
			}
	}

end JsonSupport

