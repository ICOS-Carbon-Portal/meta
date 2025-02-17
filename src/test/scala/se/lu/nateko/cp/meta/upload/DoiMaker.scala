package se.lu.nateko.cp.meta.upload

import akka.Done
import akka.actor.ActorSystem
import se.lu.nateko.cp.doi.*
import se.lu.nateko.cp.doi.core.{DoiClient, DoiClientConfig, DoiMemberConfig, PlainJavaDoiHttp}
import se.lu.nateko.cp.doi.meta.*
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.utils.async.executeSequentially

import java.net.URI
import scala.concurrent.Future

class DoiMaker(password: String)(implicit val system: ActorSystem){

	import system.dispatcher

	val client: DoiClient = {
		val conf = DoiClientConfig(
			restEndpoint = URI("https://api.datacite.org/"),
			member = DoiMemberConfig("SND.ICOS", password, "10.18160")
		)
		val http = new PlainJavaDoiHttp(Some(conf.member.symbol), Some(password))
		new DoiClient(conf, http)
	}

	val sparqlHelper = new SparqlHelper(new URI("https://meta.icos-cp.eu/sparql"))

	def saveDoi(meta: DoiMeta): Future[Done] = {
		client.putMetadata(meta).map(_ => Done)
	}

	def saveDois(infos: Seq[DoiMeta]): Future[Done] = executeSequentially(infos)(saveDoi).map(_ => Done)

}

object DoiMaker{

	val ccby4 = Rights("CC BY 4.0", Some(CpVocab.CCBY4.toString))
	val etc = GenericName("ICOS Ecosystem Thematic Centre")
	val atc = GenericName("ICOS Atmosphere Thematic Centre")

	def coolDoi(hash: Sha256Sum): String = {
		val id: Long = hash.getBytes.take(8).foldLeft(0L){(acc, b) => (acc << 8) + b}
		CoolDoi.makeRandom(id)
	}
}
