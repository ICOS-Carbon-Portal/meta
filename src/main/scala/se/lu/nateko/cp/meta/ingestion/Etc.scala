package se.lu.nateko.cp.meta.ingestion

import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.PostMethod
import spray.json._
import DefaultJsonProtocol._
import se.lu.nateko.cp.meta.utils.sesame.Loading
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import org.semarglproject.vocab.XSD
import org.openrdf.model.ValueFactory
import org.openrdf.model.Statement
import org.openrdf.model.URI
import org.openrdf.model.vocabulary.RDF

case class EtcStation(name: String, latitude: Double, longitude: Double, site: String, ecosystem: String)

object Etc extends Ingester{

	val url = "http://www.europe-fluxdata.eu/GService.asmx/getIcosSites"

	implicit val etcStationFormat = jsonFormat5(EtcStation)

	def getStations: Seq[EtcStation] = {
		val client = new HttpClient()
		val method = new PostMethod(url)

		method.setRequestHeader("Content-Type", "application/json; charset=utf-8")
		method.setRequestHeader("Content-Length", "0")

		client.executeMethod(method)
		val body = method.getResponseBodyAsString

		val json = body.parseJson
		json.asJsObject.getFields("d").head.convertTo[Seq[EtcStation]]
	}

	//TODO Make new uri production based on values, not random
	def getStatements(valueFactory: ValueFactory, newUriMaker: URI => URI): Iterator[Statement] = {
		val vocab = new Vocab(valueFactory)

		getStations.iterator.flatMap(station => {
			val uri = newUriMaker(vocab.ecoStation)

			val name = valueFactory.createLiteral(station.name, XSD.STRING)
			val lat = valueFactory.createLiteral(station.latitude.toString, XSD.DOUBLE)
			val lon = valueFactory.createLiteral(station.longitude.toString, XSD.DOUBLE)

			Iterator(
				(RDF.TYPE, vocab.ecoStation),
				(vocab.hasName, name),
				(vocab.hasLatitude, lat),
				(vocab.hasLongitude, lon)
			).map{
				case (pred, obj) => valueFactory.createStatement(uri, pred, obj)
			}
		})
	}
}