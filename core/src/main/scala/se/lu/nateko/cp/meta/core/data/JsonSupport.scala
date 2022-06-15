package se.lu.nateko.cp.meta.core.data

import se.lu.nateko.cp.meta.core.CommonJsonSupport
import se.lu.nateko.cp.meta.core.crypto.JsonSupport.given
import se.lu.nateko.cp.meta.core.toTypedJson
import spray.json.*
import DefaultJsonProtocol.*

object JsonSupport extends CommonJsonSupport{

	given RootJsonFormat[UriResource] = jsonFormat3(UriResource.apply)
	given RootJsonFormat[Project] = jsonFormat2(Project.apply)
	given RootJsonFormat[DataTheme] = jsonFormat3(DataTheme.apply)
	given RootJsonFormat[PlainStaticObject] = jsonFormat3(PlainStaticObject.apply)
	given JsonFormat[DatasetClass] = enumFormat(DatasetClass.valueOf, DatasetClass.values)
	given RootJsonFormat[DatasetSpec] = jsonFormat3(DatasetSpec.apply)
	given RootJsonFormat[DataObjectSpec] = jsonFormat9(DataObjectSpec.apply)

	given RootJsonFormat[Position] = jsonFormat4(Position.apply)
	given RootJsonFormat[LatLonBox] = jsonFormat4(LatLonBox.apply)
	given RootJsonFormat[GeoTrack] = jsonFormat2(GeoTrack.apply)
	given RootJsonFormat[Polygon] = jsonFormat2(Polygon.apply)
	given RootJsonFormat[Circle] = jsonFormat3(Circle.apply)

	given JsonFormat[CountryCode] with{
		def write(cc: CountryCode): JsValue = JsString(cc.code)
		def read(v: JsValue): CountryCode = v match{
			case JsString(CountryCode(cc)) => cc
			case _ => deserializationError(s"Expected an ISO ALPHA-2 country code string, got ${v.compactPrint}")
		}
	}

	private object vanillaGeoFeatureFormat extends RootJsonFormat[GeoFeature]{
		def write(geo: GeoFeature): JsValue = geo match {
			case llb: LatLonBox => llb.toJson
			case gt: GeoTrack => gt.toJson
			case pos: Position => pos.toJson
			case gpoly: Polygon => gpoly.toJson
			case geocol: FeatureCollection => geocol.toJson
			case c: Circle => c.toJson
		}

		def read(value: JsValue): GeoFeature = value match {
			case JsObject(fields) =>
				if(fields.contains("points"))
					value.convertTo[GeoTrack]
				else if(fields.contains("vertices"))
					value.convertTo[Polygon]
				else if(fields.contains("min") && fields.contains("max"))
					value.convertTo[LatLonBox]
				else if(fields.contains("lat") && fields.contains("lon"))
					value.convertTo[Position]
				else if(fields.contains("features"))
					value.convertTo[FeatureCollection]
				else if(fields.contains("radius"))
					value.convertTo[Circle]
				else
					deserializationError(s"Unexpected GeoFeature JsObject ${value.compactPrint}")
			case _ =>
				deserializationError("Expected a JsObject representing a GeoFeature")
		}
	}

	given RootJsonFormat[GeoFeature] with{
		def write(geo: GeoFeature): JsValue = {
			val base = vanillaGeoFeatureFormat.write(geo)
			val geoJson = GeoJson.fromFeatureWithLabels(geo)
			val allFields = base.asJsObject.fields + ("geo" -> geoJson)
			JsObject(allFields)
		}

		def read(value: JsValue): GeoFeature = vanillaGeoFeatureFormat.read(value)

	}

	given JsonFormat[FeatureCollection] = {
		given JsonFormat[Seq[GeoFeature]] = immSeqFormat(vanillaGeoFeatureFormat)
		jsonFormat2(FeatureCollection.apply)
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

	given RootJsonFormat[Organization] = jsonFormat4(Organization.apply)
	given RootJsonFormat[Instrument] = jsonFormat8(Instrument.apply)
	given RootJsonFormat[Person] = jsonFormat5(Person.apply)
	given RootJsonFormat[Site] = jsonFormat3(Site.apply)
	given JsonFormat[FunderIdType] = enumFormat(FunderIdType.valueOf, FunderIdType.values)
	given RootJsonFormat[Funder] = jsonFormat2(Funder.apply)
	given RootJsonFormat[Funding] = jsonFormat7(Funding.apply)
	given RootJsonFormat[Station] = jsonFormat8(Station.apply)

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
	given RootJsonFormat[VarMeta] = jsonFormat4(VarMeta.apply)
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
	given RootJsonFormat[References] = jsonFormat9(References.apply)
	given RootJsonFormat[DocObject] = jsonFormat12(DocObject.apply)

	given RootJsonFormat[DataObject] with {
		private given defFormat: RootJsonFormat[DataObject] = jsonFormat13(DataObject.apply)

		def read(value: JsValue): DataObject = value.convertTo[DataObject](defFormat)

		def write(dobj: DataObject): JsValue = {
			val plain = dobj.toJson(defFormat).asJsObject
			dobj.coverage.fold(plain) { geo =>
				JsObject(plain.fields + ("coverageGeo" -> GeoJson.fromFeatureWithLabels(geo)))
			}
		}
	}

	given RootJsonFormat[StaticObject] with{
		override def read(value: JsValue): StaticObject = value match {
			case JsObject(fields)  =>
				if(fields.contains("specification"))
					value.convertTo[DataObject]
				else
					value.convertTo[DocObject]
			case _ =>
				deserializationError("Expected JS object representing a data/doc object")
		}
		override def write(so: StaticObject): JsValue = so match{
			case dobj: DataObject => dobj.toJson
			case doc: DocObject => doc.toJson
		}
	}

	given RootJsonFormat[StaticCollection] = jsonFormat9(StaticCollection.apply)

	given RootJsonFormat[StaticDataItem] with{

		def write(sdi: StaticDataItem): JsValue = sdi match{
			case pdo: PlainStaticObject => pdo.toJson
			case sc: StaticCollection => sc.toJson
		}

		def read(value: JsValue): StaticDataItem = value match {
			case JsObject(fields) =>
				if(fields.contains("title"))
					value.convertTo[StaticCollection]
				else
					value.convertTo[PlainStaticObject]
			case _ =>
				deserializationError("Expected JS object representing static collection or a plain data object")
		}
	}

	import CommonJsonSupport.TypeField

	given JsonFormat[IcosStationClass] = enumFormat(IcosStationClass.valueOf, IcosStationClass.values)
	given RootJsonFormat[AtcStationSpecifics] = jsonFormat8(AtcStationSpecifics.apply)
	given RootJsonFormat[EtcStationSpecifics] = jsonFormat14(EtcStationSpecifics.apply)
	given RootJsonFormat[OtcStationSpecifics] = jsonFormat7(OtcStationSpecifics.apply)
	given RootJsonFormat[SitesStationSpecifics] = jsonFormat6(SitesStationSpecifics.apply)

	private val AtcSpec = "atc"
	private val EtcSpec = "etc"
	private val OtcSpec = "otc"
	private val SitesSpec = "sites"
	given RootJsonFormat[StationSpecifics] with{
		def write(ss: StationSpecifics): JsValue = ss match{
			case NoStationSpecifics => JsObject.empty
			case etc: AtcStationSpecifics => etc.toTypedJson(AtcSpec)
			case etc: EtcStationSpecifics => etc.toTypedJson(EtcSpec)
			case etc: OtcStationSpecifics => etc.toTypedJson(OtcSpec)
			case sites: SitesStationSpecifics => sites.toTypedJson(SitesSpec)
		}

		def read(value: JsValue) =
			value.asJsObject("StationSpecifics must be a JSON object").fields.get(TypeField) match{
				case Some(JsString(AtcSpec)) => value.convertTo[AtcStationSpecifics]
				case Some(JsString(EtcSpec)) => value.convertTo[EtcStationSpecifics]
				case Some(JsString(OtcSpec)) => value.convertTo[OtcStationSpecifics]
				case Some(JsString(SitesSpec)) => value.convertTo[SitesStationSpecifics]
				case None => NoStationSpecifics
				case Some(unknType) => deserializationError(s"Unknown StationSpecifics type $unknType")
			}
	}

}
