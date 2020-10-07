package se.lu.nateko.cp.meta.upload

import se.lu.nateko.cp.doi.core.DoiClient
import se.lu.nateko.cp.doi.core.DoiClientConfig
import java.net.URL
import se.lu.nateko.cp.doi.core.PlainJavaDoiHttp
import se.lu.nateko.cp.doi._
import se.lu.nateko.cp.doi.meta._
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import akka.actor.ActorSystem
import java.net.URI
import se.lu.nateko.cp.meta.utils.async.executeSequentially
import scala.concurrent.Future
import akka.Done
import se.lu.nateko.cp.meta.services.upload.UploadService
import scala.util.Try

class DoiMaker(password: String)(implicit val system: ActorSystem){

	import system.dispatcher

	val client: DoiClient = {
		val conf = DoiClientConfig("SND.ICOS", password, new URL("https://mds.datacite.org/"), "10.18160")
		val http = new PlainJavaDoiHttp(conf.symbol, password)
		new DoiClient(conf, http)
	}

	val sparqlHelper = new SparqlHelper(new URI("https://meta.icos-cp.eu/sparql"))

	def setDoi(info: DoiMaker.DoiInfo): Future[Done] = {
		val (meta, target) = info
		client.setDoi(meta, target.toURL).map(_ => Done)
	}

	def setDois(infos: Seq[DoiMaker.DoiInfo]): Future[Done] = executeSequentially(infos)(setDoi)

	def collectionDoi(items: Seq[URI]): Try[Doi] = UploadService.collectionHash(items).map{hash =>
		client.doi(DoiMaker.coolDoi(hash))
	}

}

object DoiMaker{
	type DoiInfo = (DoiMeta, URI)

	val cc4by = Rights("CC4.0BY", Some("https://creativecommons.org/licenses/by/4.0"))
	val etc = GenericName("ICOS Ecosystem Thematic Centre")
	val atc = GenericName("ICOS Atmosphere Thematic Centre")

	def coolDoi(hash: Sha256Sum): String = {
		val id: Long = hash.getBytes.take(8).foldLeft(0L){(acc, b) => (acc << 8) + b}
		CoolDoi.makeRandom(id)
	}
}
