package se.lu.nateko.cp.meta.services

import akka.http.scaladsl.model.Uri
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.DataProductionDto
import se.lu.nateko.cp.meta.DocObjectDto
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.ReferencesDto
import se.lu.nateko.cp.meta.SpatioTemporalDto
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.StationTimeSeriesDto
import se.lu.nateko.cp.meta.UploadDto
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import se.lu.nateko.cp.meta.utils.*

import java.net.URI
import java.time.Instant
import scala.util.Success

import UriSerializer.Hash

class UploadDtoReader(uriSer: UriSerializer){
	import UploadDtoReader.*

	def readDto(uri: Uri): Option[UploadDto] = uri.path match{
		case Hash.Object(_) =>
			uriSer.fetchStaticObject(uri).map(objToDto)

		case Hash.Collection(_) =>
			uriSer.fetchStaticCollection(uri).map(collToDto)

		case _ => None
	}
}

object UploadDtoReader{
	def objToDto(obj: StaticObject) = obj match {
		case dobj: DataObject => DataObjectDto(
			submitterId = "",
			hashSum = dobj.hash,
			objectSpecification = dobj.specification.self.uri,
			fileName = dobj.fileName,
			specificInfo = dobj.specificInfo match {
				case Left(l3) => Left(SpatioTemporalDto(
					title = l3.title,
					description = l3.description,
					spatial = readCoverage(l3.spatial),
					temporal = l3.temporal,
					forStation = l3.station.map(_.org.self.uri),
					samplingHeight = l3.samplingHeight,
					production = dataProductionToDto(l3.productionInfo),
					customLandingPage = dobj.accessUrl.filterNot(uri => uri.getPath.endsWith(dobj.hash.id)),
					variables = l3.variables.map(_.map(_.label))
				))
				case Right(l2) => Right(StationTimeSeriesDto(
					station = l2.acquisition.station.org.self.uri,
					site = l2.acquisition.site.map(_.self.uri),
					instrument = l2.acquisition.instrument.map(transformEither(_.uri, _.map(_.uri))),
					samplingPoint = l2.acquisition.samplingPoint,
					samplingHeight = l2.acquisition.samplingHeight,
					acquisitionInterval = l2.acquisition.interval,
					nRows = l2.nRows,
					production = l2.productionInfo.map(dataProductionToDto(_))
				))
			},
			isNextVersionOf = Option(Right(dobj.previousVersion.flattenToSeq.flatMap{uri =>
				Uri.Path(uri.getPath) match {
					case Hash.Object(hash) => Some(hash)
					case _ => None
				}
			})),
			preExistingDoi = dobj.doi.map(Doi.parse).collect{
				case Success(doi) => doi
			},
			references = refsToDto(dobj)
		)
		case dobj: DocObject => DocObjectDto(
			submitterId = "",
			hashSum = dobj.hash,
			fileName = dobj.fileName,
			title = dobj.references.title,
			description = dobj.description,
			authors = dobj.references.authors.fold[Seq[URI]](Seq())(_.map(_.self.uri)),
			isNextVersionOf = Option(Right(dobj.previousVersion.flattenToSeq.flatMap{uri =>
				Uri.Path(uri.getPath) match {
					case Hash.Object(hash) => Some(hash)
					case _ => None
				}
			})),
			preExistingDoi = dobj.doi.map(Doi.parse).collect{
				case Success(doi) => doi
			},
			references = refsToDto(dobj)
		)
	}

	private def refsToDto(obj: StaticObject): Option[ReferencesDto] = Some(
		ReferencesDto(
			keywords = obj.references.keywords,
			licence = obj.references.licence.map(_.url),
			moratorium = obj.submission.stop.filter(_.compareTo(Instant.now()) > 0),
			duplicateFilenameAllowed = None,
			autodeprecateSameFilenameObjects = None,
			nextVersionIsPartial = None
		)
	)

	def collToDto(coll: StaticCollection) = StaticCollectionDto(
		submitterId = "",
		members = coll.members.map(_ match {
			case pso: PlainStaticObject=> pso.res
			case sc: StaticCollection => sc.res
		}),
		title = coll.title,
		description = coll.description,
		isNextVersionOf = coll.previousVersion.flatMap{uri =>
			Uri.Path(uri.getPath) match {
				case Hash.Collection(hash) => Some(Left(hash))
				case Hash.Object(hash) => Some(Left(hash))
				case _ => None
			}
		},
		preExistingDoi = coll.doi.map(Doi.parse).collect{
			case Success(doi) => doi
		}
	)

	private def dataProductionToDto(prod: DataProduction) = DataProductionDto(
		creator = prod.creator.self.uri,
		contributors = prod.contributors.map(_.self.uri),
		hostOrganization = prod.host.map(_.self.uri),
		comment = prod.comment,
		sources = Option(prod.sources.map(_.hash)),
		documentation = prod.documentation.map(_.hash),
		creationDate = prod.dateTime
	)

	private def readCoverage(gf: GeoFeature): Either[GeoFeature, URI] = gf.uri match
		case None      => Left(gf)
		case Some(uri) => Right(uri)
}
