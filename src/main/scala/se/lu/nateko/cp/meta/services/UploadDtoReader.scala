package se.lu.nateko.cp.meta.services

import akka.http.scaladsl.model.Uri
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.UploadDto
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import se.lu.nateko.cp.doi.Doi
import scala.util.Success
import se.lu.nateko.cp.meta.core.data.PlainStaticObject
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

import UriSerializer.Hash
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.DataObjectDto
import se.lu.nateko.cp.meta.ElaboratedProductMetadata
import se.lu.nateko.cp.meta.DataProductionDto
import se.lu.nateko.cp.meta.core.data.L2OrLessSpecificMeta
import se.lu.nateko.cp.meta.StationDataMetadata
import se.lu.nateko.cp.meta.utils._
import se.lu.nateko.cp.meta.core.data.DocObject
import se.lu.nateko.cp.meta.DocObjectDto
import se.lu.nateko.cp.meta.core.data.DataProduction

class UploadDtoReader(uriSer: UriSerializer){
	import UploadDtoReader._

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
				case Left(l3) => Left(ElaboratedProductMetadata(
					title = l3.title,
					description = l3.description,
					spatial = Left(l3.spatial),
					temporal = l3.temporal,
					production = dataProductionToDto(l3.productionInfo),
					customLandingPage = None))
				case Right(l2) => Right(StationDataMetadata(
					station = l2.acquisition.station.org.self.uri,
					site = l2.acquisition.site.map(_.self.uri),
					instrument = l2.acquisition.instrument,
					samplingPoint = l2.coverage.map(Left(_)),
					samplingHeight = l2.acquisition.samplingHeight,
					acquisitionInterval = l2.acquisition.interval,
					nRows = l2.nRows,
					production = l2.productionInfo.map(dataProductionToDto(_))
				))
			},
			isNextVersionOf = Option(Right(dobj.previousVersion.flattenToSeq.flatMap{uri =>
				Uri.Path(uri.getPath) match {
					case Hash.Object(hash) => Some(hash)
				}
			})),
			preExistingDoi = dobj.doi.map(Doi.parse).collect{
				case Success(doi) => doi
			}
		)
		case dobj: DocObject => DocObjectDto(
			submitterId = "",
			hashSum = dobj.hash,
			fileName = dobj.fileName,
			isNextVersionOf = Option(Right(dobj.previousVersion.flattenToSeq.flatMap{uri =>
				Uri.Path(uri.getPath) match {
					case Hash.Object(hash) => Some(hash)
				}
			})),
			preExistingDoi = dobj.doi.map(Doi.parse).collect{
				case Success(doi) => doi
			}
		)
	}

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
		sources = Option(prod.sources.flatMap{uri =>
			Uri.Path(uri.uri.getPath) match {
				case Hash.Object(hash) => Some(hash)
				case _ => None
			}
		}),
		creationDate = prod.dateTime
	)

}
