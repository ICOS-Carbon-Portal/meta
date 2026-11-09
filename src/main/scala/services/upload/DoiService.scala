package se.lu.nateko.cp.meta.services.upload
import akka.http.scaladsl.model.Uri
import eu.icoscp.envri.Envri
import se.lu.nateko.cp.doi.{Doi, DoiMeta}
import se.lu.nateko.cp.meta.DoiConfig
import se.lu.nateko.cp.meta.core.data.{DataObject, DocObject, PlainStaticCollection, PlainStaticObject, StaticCollection, StaticObject}
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer
import se.lu.nateko.cp.meta.services.metaexport.DataCite
import se.lu.nateko.cp.meta.utils.Validated

import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

class DoiService(doiConf: DoiConfig, fetcher: UriSerializer)(using ExecutionContext) {

	private val doiClientFactory = DoiClientFactory(doiConf)

	private def client(using Envri) = doiClientFactory.getClient

	private def saveDoi(meta: DoiMeta)(using Envri): Future[Unit] = client.putMetadata(meta)

	private def fetchCollObjectsRecursively(coll: StaticCollection): Future[Validated[Seq[StaticObject]]] =
		Future.sequence:
			coll.members.map:
				case dobj: PlainStaticObject =>
					fetcher.fetchStaticObject(Uri(dobj.res.toString))
						.map(_.map(Seq(_)))
				case coll: PlainStaticCollection =>
					fetcher.fetchStaticCollection(Uri(coll.res.toString))
						.flatMap(flatMapAsync(_)(fetchCollObjectsRecursively))
		.map(Validated.sequence(_).map(_.flatten))

	def createDraftDoi(dataItemLandingPage: URI)(using Envri): Future[Validated[Doi]] =
		import UriSerializer.Hash
		val uri = Uri(dataItemLandingPage.toString)
		val dataCite = DataCite(s => client.doi(s), fetchCollObjectsRecursively)

		val doiMetaVFut: Future[Validated[DoiMeta]] =
			(uri.path match
				case Hash.Collection(_)  => fetcher.fetchStaticCollection(uri)
					.flatMap(flatMapAsync(_)(dataCite.makeCollectionDoi))

				case Hash.Object(_)      => fetcher.fetchStaticObject(uri)
					.map(_.map:
						case data: DataObject => dataCite.makeDataObjectDoi(data)
						case doc: DocObject   => dataCite.makeDocObjectDoi(doc)
					)

				case _ => Future.successful(Validated.error(s"URI $uri is neither collection nor data/document object"))
			)
			.map(_.flatMap(requireCreators).map(_.copy(url = Some(dataItemLandingPage.toString))))

		doiMetaVFut.flatMap: doiMetaV =>
			val doiV = doiMetaV.map(_.doi)

			doiMetaV.result.fold(Future.successful(doiV)): m =>
				client.putMetadata(m).map(_ => doiV)

	private def requireCreators(meta: DoiMeta): Validated[DoiMeta] =
		if meta.creators.isEmpty
		then Validated.error("Refusing to create a DOI without authors (creators)")
		else Validated.ok(meta)

	private def flatMapAsync[T, U](v: Validated[T])(f: T => Future[Validated[U]]): Future[Validated[U]] =
		v.map(f).liftFuture.map(_.flatten)

}
