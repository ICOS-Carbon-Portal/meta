package se.lu.nateko.cp.meta.services.upload
import akka.http.scaladsl.model.Uri
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.doi.{Doi, DoiMeta}
import se.lu.nateko.cp.meta.DoiConfig
import se.lu.nateko.cp.meta.core.data.{DataObject, DocObject, PlainStaticCollection, PlainStaticObject, StaticCollection, StaticObject}
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import se.lu.nateko.cp.meta.services.metaexport.DataCite
import se.lu.nateko.cp.meta.services.derived.DerivedMetadataClient
import se.lu.nateko.cp.meta.utils.Validated

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

class DoiService(doiConf: DoiConfig, fetcher: UriSerializer, derivedMetadata: DerivedMetadataClient)(using ExecutionContext) {

	private val doiClientFactory = DoiClientFactory(doiConf)

	private def client(using Envri) = doiClientFactory.getClient

	private def saveDoi(meta: DoiMeta)(using Envri): Future[Unit] = client.putMetadata(meta)

	private def enrich[T](parsed: Validated[T])(enricher: T => Future[T]): Future[Validated[T]] =
		parsed.result.fold(Future.successful(new Validated[T](None, parsed.errors))): item =>
			enricher(item).map(enriched => new Validated(Some(enriched), parsed.errors))

	private def fetchCollObjectsRecursively(coll: StaticCollection): Future[Validated[Seq[StaticObject]]] =
		Future.sequence:
			coll.members.map:
				case dobj: PlainStaticObject =>
					enrich(fetcher.fetchStaticObject(Uri(dobj.res.toString)))(derivedMetadata.enrich(dobj.res, _))
						.map(_.map(Seq(_)))
				case nested: PlainStaticCollection =>
					enrich(fetcher.fetchStaticCollection(Uri(nested.res.toString)))(derivedMetadata.enrich(nested.res, _))
						.flatMap: nestedV =>
							nestedV.result.fold(Future.successful(new Validated[Seq[StaticObject]](None, nestedV.errors)))(fetchCollObjectsRecursively)
		.map(Validated.sequence(_).map(_.flatten))

	def createDraftDoi(dataItemLandingPage: URI)(using Envri): Future[Validated[Doi]] =
		import UriSerializer.Hash
		val uri = Uri(dataItemLandingPage.toString)
		val dataCite = DataCite(s => client.doi(s))

		val doiMetaV: Future[Validated[DoiMeta]] = uri.path match
			case Hash.Collection(_) =>
				enrich(fetcher.fetchStaticCollection(uri))(derivedMetadata.enrich(dataItemLandingPage, _)).flatMap: collV =>
					collV.result.fold(Future.successful(new Validated[DoiMeta](None, collV.errors))): coll =>
						fetchCollObjectsRecursively(coll).map: members =>
							members.map(dataCite.makeCollectionDoi(coll, _))

			case Hash.Object(_) =>
				enrich(fetcher.fetchStaticObject(uri))(derivedMetadata.enrich(dataItemLandingPage, _)).map:
					_.flatMap:
						case data: DataObject => Validated.ok(dataCite.makeDataObjectDoi(data))
						case doc: DocObject => Validated.ok(dataCite.makeDocObjectDoi(doc))

			case _ => Future.successful(Validated.error(s"URI $uri is neither collection nor data/document object"))

		doiMetaV.flatMap: metaV =>
			val withUrl = metaV.map(_.copy(url = Some(dataItemLandingPage.toString)))
			val doiV = withUrl.map(_.doi)
			withUrl.result.fold(Future.successful(doiV)): m =>
				client.putMetadata(m).map(_ => doiV)

}
