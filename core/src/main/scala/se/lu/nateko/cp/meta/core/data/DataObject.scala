package se.lu.nateko.cp.meta.core.data

import java.net.URI
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.time.Instant
import se.lu.nateko.cp.doi.DoiMeta
import scala.collection.immutable

case class UriResource(uri: URI, label: Option[String], comments: Seq[String])

sealed trait Agent{
	def self: UriResource
	def email: Option[String]
}
case class Organization(
	self: UriResource,
	name: String,
	email: Option[String],
	website: Option[URI]
) extends Agent
case class Person(self: UriResource, firstName: String, lastName: String, email: Option[String], orcid: Option[Orcid]) extends Agent

case class Site(self: UriResource, ecosystem: UriResource, location: Option[GeoFeature])

case class Project(self: UriResource, keywords: Option[Seq[String]])
case class DataTheme(self: UriResource, icon: URI, markerIcon: Option[URI])

case class DataObjectSpec(
	self: UriResource,
	project: Project,
	theme: DataTheme,
	format: UriResource,
	encoding: UriResource,
	dataLevel: Int,
	datasetSpec: Option[DatasetSpec],
	documentation: Seq[PlainStaticObject],
	keywords: Option[Seq[String]]
){
	def isStationTimeSer: Boolean = datasetSpec.exists(_.dsClass == DatasetClass.StationTimeSeries)
	def isSpatiotemporal: Boolean = datasetSpec.exists(_.dsClass == DatasetClass.SpatioTemporal)
}

enum DatasetClass derives CanEqual:
	case StationTimeSeries, SpatioTemporal

case class DatasetSpec(
	self: UriResource,
	dsClass: DatasetClass,
	resolution: Option[String]
)

case class DataAcquisition(
	station: Station,
	site: Option[Site],
	interval: Option[TimeInterval],
	instrument: OptionalOneOrSeq[UriResource],
	samplingPoint: Option[Position],
	samplingHeight: Option[Float]
){
	def instruments: Seq[UriResource] = instrument.fold(Seq.empty[UriResource])(_.fold(Seq(_), identity))

	def coverage: Option[GeoFeature] = samplingPoint
		.map(sp => siteFeature
			.fold[GeoFeature](sp)(l => FeatureCollection(Seq(sp, l), None).flatten)
		)
		.orElse(siteFeature)
		.orElse(station.fullCoverage)

	private def siteFeature = site.flatMap(_.location)
}

case class DataProduction(
	creator: Agent,
	contributors: Seq[Agent],
	host: Option[Organization],
	comment: Option[String],
	sources: Seq[PlainStaticObject],
	documentation: Option[PlainStaticObject],
	dateTime: Instant
)
case class DataSubmission(submitter: Organization, start: Instant, stop: Option[Instant])

case class StationTimeSeriesMeta(
	acquisition: DataAcquisition,
	productionInfo: Option[DataProduction],
	nRows: Option[Int],
	coverage: Option[GeoFeature],
	columns: Option[Seq[VarMeta]]
)

case class ValueType(self: UriResource, quantityKind: Option[UriResource], unit: Option[String])
case class VarMeta(
	model: UriResource,
	label: String,
	valueType: ValueType,
	valueFormat: Option[URI],
	minMax: Option[(Double, Double)],
	instrumentDeployment: Option[InstrumentDeployment]
)

case class SpatioTemporalMeta(
	title: String,
	description: Option[String],
	spatial: LatLonBox,
	temporal: TemporalCoverage,
	station: Option[Station],
	samplingHeight: Option[Float],
	productionInfo: DataProduction,
	variables: Option[Seq[VarMeta]]
){
	def acquisition: Option[DataAcquisition] = station.map{
		DataAcquisition(_, None, Some(temporal.interval), None, None, samplingHeight)
	}
}

sealed trait StaticObject extends CitableItem{
	def hash: Sha256Sum
	def accessUrl: Option[URI]
	def pid: Option[String]
	def doi: Option[String]
	def fileName: String
	def size: Option[Long]
	def submission: DataSubmission
	def previousVersion: OptionalOneOrSeq[URI]
	def nextVersion: Option[URI]
	def parentCollections: Seq[UriResource]
	def references: References

	def asDataObject: Option[DataObject] = this match{
		case dobj: DataObject => Some(dobj)
		case _ => None
	}
}

case class DataObject(
	hash: Sha256Sum,
	accessUrl: Option[URI],
	pid: Option[String],
	doi: Option[String],
	fileName: String,
	size: Option[Long],
	submission: DataSubmission,
	specification: DataObjectSpec,
	specificInfo: Either[SpatioTemporalMeta, StationTimeSeriesMeta],
	previousVersion: OptionalOneOrSeq[URI],
	nextVersion: Option[URI],
	parentCollections: Seq[UriResource],
	references: References
) extends StaticObject{
	def acquisition: Option[DataAcquisition] = specificInfo.fold(_.acquisition, stTs => Some(stTs.acquisition))

	def production: Option[DataProduction] = specificInfo.fold(
		l3 => Some(l3.productionInfo),
		l2 => l2.productionInfo
	)

	def coverage: Option[GeoFeature] =
		val acqCov = specificInfo.fold(
			l3 => Some(l3.spatial),
			l2 => l2.coverage.orElse(l2.acquisition.coverage)
		)

		val varsAndPosits = for
			cols <- specificInfo.fold(
				l3 => l3.variables,
				l2 => l2.columns
			).toSeq
			col <- cols
			dep <- col.instrumentDeployment
			pos <- dep.pos
		yield
			pos -> dep.variableName
		
		val deploymentCov = varsAndPosits
			.groupMapReduce(
				(pos,_) => Position(pos.lat, pos.lon, None, None)
			)(
				(_, varNameOpt) => varNameOpt.getOrElse("").trim
			)(
				(s1, s2) => if s1 == "" then s2 else if s2 == "" then s1 else s"$s1 / $s2"
			).map{
				(pos, varNames) =>
					val label = Option(varNames).filterNot(_.isEmpty)
					Pin(pos.copy(label = label), PinKind.Sensor)
			}

		(acqCov.toSeq ++ deploymentCov) match
			case Seq() => None
			case Seq(single) => Some(single)
			case many => Some(FeatureCollection(many, None).flatten)
	end coverage

	def keywords: Option[Seq[String]] =
		Option((references.keywords ++ specification.keywords ++ specification.project.keywords).flatten)
			.filter(_.nonEmpty)
			.map(_.toSeq.sorted)

	def isPreviewable: Boolean = specificInfo.fold(
		spatioTemporal => spatioTemporal.variables.nonEmpty,
		stationTimeSeries => stationTimeSeries.columns.nonEmpty
	)
}

case class DocObject(
	hash: Sha256Sum,
	accessUrl: Option[URI],
	pid: Option[String],
	doi: Option[String],
	fileName: String,
	size: Option[Long],
	description: Option[String],
	submission: DataSubmission,
	previousVersion: OptionalOneOrSeq[URI],
	nextVersion: Option[URI],
	parentCollections: Seq[UriResource],
	references: References
) extends StaticObject

case class Licence(url: URI, name: String, webpage: URI, baseLicence: Option[URI])

case class References(
	citationString: Option[String],
	citationBibTex: Option[String],
	citationRis: Option[String],
	doi: Option[DoiMeta],
	keywords: Option[Seq[String]],
	authors: Option[Seq[Agent]],
	title: Option[String],
	temporalCoverageDisplay: Option[String],
	acknowledgements: Option[Seq[String]],
	licence: Option[Licence]
)
object References{
	def empty = References(None, None, None, None, None, None, None, None, None, None)
}
