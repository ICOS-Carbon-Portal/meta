package se.lu.nateko.cp.meta.services.upload

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.unmarshalling.Unmarshal
import se.lu.nateko.cp.doi.CoolDoi
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.doi.DoiMeta
import se.lu.nateko.cp.doi.core.DoiClient
import se.lu.nateko.cp.doi.core.DoiClientConfig
import se.lu.nateko.cp.doi.core.PlainJavaDoiHttp
import se.lu.nateko.cp.meta.DoiConfig
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.DataProduction
import se.lu.nateko.cp.meta.core.data.DocObject
import se.lu.nateko.cp.meta.core.data.FeatureCollection
import se.lu.nateko.cp.meta.core.data.PlainStaticObject
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer

import java.net.URI
import java.time.Year
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import java.time.Instant
import se.lu.nateko.cp.meta.services.metaexport.DataCite
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.core.data.PlainStaticCollection

class DoiService(doiConf: DoiConfig, fetcher: UriSerializer)(using ExecutionContext) {

	private val doiClientFactory = DoiClientFactory(doiConf)

	private def client(using Envri) = doiClientFactory.getClient

	private def saveDoi(meta: DoiMeta)(using Envri): Future[Unit] = client.putMetadata(meta)

	private def fetchCollObjectsRecursively(coll: StaticCollection): Validated[Seq[StaticObject]] =
		Validated.sequence:
			coll.members.map:
				case dobj: PlainStaticObject =>
					fetcher.fetchStaticObject(Uri(dobj.res.toString))
						.map(Seq(_))
				case coll: PlainStaticCollection =>
					fetcher.fetchStaticCollection(Uri(coll.res.toString))
						.flatMap(fetchCollObjectsRecursively)
		.map(_.flatten)

	def createDraftDoi(dataItemLandingPage: URI)(using Envri): Future[Validated[Doi]] =
		import UriSerializer.Hash
		val uri = Uri(dataItemLandingPage.toString)
		val dataCite = DataCite(s => client.doi(s), fetchCollObjectsRecursively)

		val doiMetaV: Validated[DoiMeta] =
			(uri.path match
				case Hash.Collection(_)  => fetcher.fetchStaticCollection(uri)
					.flatMap(dataCite.makeCollectionDoi)

				case Hash.Object(_)      => fetcher.fetchStaticObject(uri)
					.map:
						case data: DataObject => dataCite.makeDataObjectDoi(data)
						case doc: DocObject   => dataCite.makeDocObjectDoi(doc)

				case _ => Validated.error(s"URI $uri is neither collection nor data/document object")
			)
			.map(_.copy(url = Some(dataItemLandingPage.toString)))

		val doiV = doiMetaV.map(_.doi)

		doiMetaV.result.fold(Future.successful(doiV)): m =>
			client.putMetadata(m).map(_ => doiV)

}
