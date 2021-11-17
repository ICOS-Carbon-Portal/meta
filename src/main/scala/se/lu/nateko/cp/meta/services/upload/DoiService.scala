package se.lu.nateko.cp.meta.services.upload

import akka.actor.ActorSystem
import se.lu.nateko.cp.doi.core.DoiClient
import se.lu.nateko.cp.doi.core.PlainJavaDoiHttp
import se.lu.nateko.cp.doi.core.DoiClientConfig
import se.lu.nateko.cp.doi.DoiMeta
import scala.concurrent.Future
import se.lu.nateko.cp.doi.CoolDoi
import se.lu.nateko.cp.meta.CpmetaConfig
import se.lu.nateko.cp.doi.Doi
import se.lu.nateko.cp.meta.core.data.DataObject
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import se.lu.nateko.cp.doi.meta._
import se.lu.nateko.cp.meta.core.data.JsonSupport._
import se.lu.nateko.cp.meta.core.data.DocObject
import se.lu.nateko.cp.meta.core.data.StaticCollection
import se.lu.nateko.cp.meta.core.data.Person
import se.lu.nateko.cp.meta.core.data.Organization
import se.lu.nateko.cp.doi.meta.GenericName
import java.time.Year
import se.lu.nateko.cp.meta.core.data.StaticObject
import se.lu.nateko.cp.meta.core.data.Envri.Envri
import se.lu.nateko.cp.meta.services.linkeddata.UriSerializer

class DoiService(conf: CpmetaConfig)(implicit val system: ActorSystem) {

	import system.dispatcher

	private def client(implicit envri: Envri): DoiClient = {
		val doiConf = conf.doi(envri)
		val http = new PlainJavaDoiHttp(doiConf.symbol, doiConf.password)
		val clientConf = new DoiClientConfig(doiConf.symbol, doiConf.password, doiConf.restEndpoint.toURL(), doiConf.prefix)
		new DoiClient(clientConf, http)
	}

	private val ccby4 = Rights("CC BY 4.0", Some("https://creativecommons.org/licenses/by/4.0"))

	def saveDoi(meta: DoiMeta)(implicit envri: Envri): Future[Doi] = {
		client.putMetadata(meta).map(_ => meta.doi)
	}

	def makeDoi(
		doiType: String,
		uri: Uri,
		uriSerializer: UriSerializer
	)(implicit envri: Envri): Option[DoiMeta] = doiType match {
		case "data" => makeDataObjectDoi(uri, uriSerializer)
		case "document" => makeDocObjectDoi(uri, uriSerializer)
		case "collection" => makeCollectionDoi(uri, uriSerializer)
		case _ => None
	}

	private def makeDataObjectDoi(uri: Uri, uriSerializer: UriSerializer)(implicit envri: Envri) = {
		uriSerializer.fetchStaticObject(uri).flatMap(_.asDataObject).map{dobj =>
			DoiMeta(
				doi = client.doi(CoolDoi.makeRandom),
				creators = dobj.references.authors.fold[Seq[Creator]](Seq())(_.map(a =>
					Creator(PersonalName(a.firstName, a.lastName), Seq(), Seq()))
				),
				titles = dobj.references.title.map(t => Seq(Title(t, None, None))),
				publisher = Some("ICOS ERIC -- Carbon Portal"),
				publicationYear = Some(Year.now.getValue),
				types = Some(ResourceType(None, Some(ResourceTypeGeneral.Dataset))),
				subjects = Seq(),
				contributors = dobj.specificInfo match {
					case Left(l3) => l3.productionInfo.contributors.map(_ match {
						case Person(_, firstName, lastName, _) => Contributor(PersonalName(firstName, lastName), Seq(), Seq(), None)
						case Organization(_, name, _, _) => Contributor(GenericName(name), Seq(), Seq(), None)
					})
					case Right(_) => Seq()
				},
				dates = Seq(
					Date(java.time.Instant.now.toString.take(10), DateType.Issued)
				),
				formats = Seq(),
				version = Some(Version(1, 0)),
				rightsList = Some(Seq(ccby4)),
				descriptions = dobj.specificInfo match {
					case Left(l3) => l3.description.map(d => Description(d, DescriptionType.Abstract, None)).toSeq
					case Right(_) => Seq()
				},
				url = Some(uri.toString)
			)
		}
	}

	private def makeDocObjectDoi(uri: Uri, uriSerializer: UriSerializer)(implicit envri: Envri): Option[DoiMeta] = {
		uriSerializer.fetchStaticObject(uri).map{dobj =>
			DoiMeta(
				doi = client.doi(CoolDoi.makeRandom),
				titles = dobj.references.title.map(title => Seq(Title(title, None, None)))
			)
		}
	}

	private def makeCollectionDoi(uri: Uri, uriSerializer: UriSerializer)(implicit envri: Envri): Option[DoiMeta] = {
		uriSerializer.fetchStaticCollection(uri).map{coll =>
			DoiMeta(
				doi = client.doi(CoolDoi.makeRandom),
				creators = Seq(Creator(GenericName(coll.creator.name), Seq(), Seq())),
				titles = Some(Seq(Title(coll.title, None, None))),
				publisher = Some("ICOS ERIC -- Carbon Portal"),
				publicationYear = Some(Year.now.getValue),
				types = Some(ResourceType(Some("ZIP archives"), Some(ResourceTypeGeneral.Collection))),
				subjects = Seq(),
				contributors = Seq(),
				dates = Seq(
					Date(java.time.Instant.now.toString.take(10), DateType.Issued)
				),
				formats = Seq(),
				version = Some(Version(1, 0)),
				rightsList = Some(Seq(ccby4)),
				descriptions = coll.description.map(d => Description(d, DescriptionType.Abstract, None)).toSeq,
				url = Some(uri.toString)
			)
		}
	}
}
