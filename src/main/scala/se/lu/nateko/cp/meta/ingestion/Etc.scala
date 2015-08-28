package se.lu.nateko.cp.meta.ingestion

import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.PostMethod
import spray.json._
import DefaultJsonProtocol._
import se.lu.nateko.cp.meta.utils.sesame.Loading
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import org.openrdf.model.ValueFactory
import org.openrdf.model.Statement
import org.openrdf.model.URI
import org.openrdf.model.vocabulary.RDF
import java.net.URLEncoder
import org.openrdf.model.vocabulary.XMLSchema

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

	def getStatements(valueFactory: ValueFactory): Iterator[Statement] = {
		val vocab = Vocab(valueFactory)

		getStations.iterator.flatMap(station => {
			val uri = valueFactory.createURI(vocab.ecoStationClass.stringValue + "/" + URLEncoder.encode(station.site, "UTF-8"))

			val name = valueFactory.createLiteral(station.name, XMLSchema.STRING)
			val stationId = valueFactory.createLiteral(station.site, XMLSchema.STRING)
			val lat = valueFactory.createLiteral(station.latitude.toString, XMLSchema.DOUBLE)
			val lon = valueFactory.createLiteral(station.longitude.toString, XMLSchema.DOUBLE)

			Iterator(
				(RDF.TYPE, vocab.ecoStationClass),
				(vocab.hasName, name),
				(vocab.hasStationId, stationId),
				(vocab.hasLatitude, lat),
				(vocab.hasLongitude, lon)
			).map{
				case (pred, obj) => valueFactory.createStatement(uri, pred, obj)
			}
		})
	}
}