package se.lu.nateko.cp.meta.services

import akka.http.scaladsl.model.Uri
import se.lu.nateko.cp.meta.ObjectUploadDto
import se.lu.nateko.cp.meta.StaticCollectionDto
import se.lu.nateko.cp.meta.UploadDto
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer

class UploadDtoReader(uriSer: UriSerializer){
	import UriSerializer.Hash
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
	def objToDto(obj: StaticObject): ObjectUploadDto = ???
	def collToDto(coll: StaticCollection): StaticCollectionDto = ???
}
