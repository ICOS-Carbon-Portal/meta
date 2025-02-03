package se.lu.nateko.cp.meta.services.upload

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport.*
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.{HttpRequest, MediaTypes, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.doi.core.{DoiClient, DoiClientConfig, PlainJavaDoiHttp}
import se.lu.nateko.cp.doi.{CoolDoi, Doi, DoiMeta}
import se.lu.nateko.cp.meta.DoiConfig
import se.lu.nateko.cp.meta.core.data.{DataObject, DataProduction, DocObject, FeatureCollection, PlainStaticCollection, PlainStaticObject, StaticCollection, StaticObject}
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import se.lu.nateko.cp.meta.services.metaexport.DataCite
import se.lu.nateko.cp.meta.utils.Validated

import java.net.URI
import java.time.{Instant, Year}
import scala.concurrent.{ExecutionContext, Future}

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
