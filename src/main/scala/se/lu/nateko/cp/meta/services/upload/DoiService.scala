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
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.meta.core.data.DataObject
import se.lu.nateko.cp.meta.core.data.DataProduction
import se.lu.nateko.cp.meta.core.data.DocObject
import se.lu.nateko.cp.meta.core.data.Envri
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
import se.lu.nateko.cp.meta.services.metaexport

class DoiService(conf: CpmetaConfig, fetcher: UriSerializer)(implicit ctxt: ExecutionContext) {

	private def client(implicit envri: Envri): DoiClient = {
		val doiConf = conf.doi(envri)
		val http = new PlainJavaDoiHttp(doiConf.symbol, doiConf.password)
		val clientConf = new DoiClientConfig(doiConf.symbol, doiConf.password, doiConf.restEndpoint.toURL(), doiConf.prefix)
		new DoiClient(clientConf, http)
	}

	private def saveDoi(meta: DoiMeta)(implicit envri: Envri): Future[Doi] =
		client.putMetadata(meta).map(_ => meta.doi)

	private def fetchCollObjectsRecursively(coll: StaticCollection): Seq[StaticObject] = coll.members.flatMap{
		case plain: PlainStaticObject => fetcher.fetchStaticObject(Uri(plain.res.toString))
		case coll: StaticCollection => fetchCollObjectsRecursively(coll)
	}

	def createDraftDoi(dataItemLandingPage: URI)(implicit envri: Envri): Future[Option[Doi]] = {
		import UriSerializer.Hash
		val uri = Uri(dataItemLandingPage.toString)
		val dataCite = DataCite(s => client.doi(s), fetchCollObjectsRecursively)

		val doiMetaOpt: Option[DoiMeta] =
			(uri.path match {
				case Hash.Collection(_)  => fetcher.fetchStaticCollection(uri)
					.map(dataCite.makeCollectionDoi)

				case Hash.Object(_)      => fetcher.fetchStaticObject(uri)
					.map{
						case data: DataObject => dataCite.makeDataObjectDoi(data)
						case doc: DocObject   => dataCite.makeDocObjectDoi(doc)
					}
				case _ => None
			})
			.map(_.copy(url = Some(dataItemLandingPage.toString)))

		doiMetaOpt.fold(Future.successful(Option.empty[Doi]))(m => saveDoi(m).map(Some(_)))
	}

}
