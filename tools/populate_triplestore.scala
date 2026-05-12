import scala.language.unsafeNulls

import org.slf4j.LoggerFactory
import org.eclipse.rdf4j.model.Statement

private val log = LoggerFactory.getLogger("tools.populateTriplestore")

private val ChunkSize = 100000

@main def populateTriplestore(args: String*): Unit = {
	import se.lu.nateko.cp.meta.{ConfigLoader, MetaDb}
	import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
	import org.eclipse.rdf4j.model.impl.SimpleValueFactory

	val config = ConfigLoader.default
	val virtuosoConf = config.virtuoso
	given factory: SimpleValueFactory = SimpleValueFactory.getInstance()

	val graphUpdateEndpoint = s"${virtuosoConf.host}/sparql-graph-crud-auth"
	val graphStore = new HttpGraphStore(graphUpdateEndpoint, virtuosoConf.username, virtuosoConf.password)

	val allConfs = MetaDb.getAllInstanceServerConfigs(config.instanceServers)
	val selectedConfs = args.headOption.fold(allConfs) { id =>
		allConfs.get(id).map(id -> _).toMap
	}

	for {
		(_id, conf) <- selectedConfs
		logName <- conf.logName
	} do {
		val rdfLog = PostgresRdfLog(logName, config.rdfLog, factory)
		val graphUri = conf.writeContext.toString

		log.info(s"$graphUri: clearing")
		if (graphStore.clear(graphUri)) {
			log.info(s"$graphUri: cleared")
		} else {
			log.info("$graphUri: did not exist")
		}

		var written = 0
		rdfLog.updates.grouped(ChunkSize).foreach { chunk =>
			graphStore.upload(graphUri, chunk.map(_.statement))
			written += chunk.size
			log.info(s"$logName: ${written / 1000}k statements written")
		}
		log.info(s"Ingesting from RDF log $logName done!")
	}
	graphStore.close()
	println(s"ALL DONE!")
}

trait GraphStore extends AutoCloseable {
	def clear(graphUri: String): Boolean
	def upload(graphUri: String, statements: Seq[Statement]): Unit
}

class HttpGraphStore(baseEndpoint: String, username: String, password: String) extends GraphStore {
	import java.net.URLEncoder
	import java.io.ByteArrayOutputStream
	import scala.util.Using
	import org.apache.http.HttpResponse
	import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
	import org.apache.http.client.methods.{HttpDelete, HttpPost, HttpUriRequest}
	import org.apache.http.entity.{ByteArrayEntity, ContentType}
	import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClients}
	import org.apache.http.util.EntityUtils
	import org.eclipse.rdf4j.rio.{Rio, RDFFormat}

	private val httpClient = {
		val credsProvider = new BasicCredentialsProvider()
		credsProvider.setCredentials(
			AuthScope.ANY,
			new UsernamePasswordCredentials(username, password)
		)
		HttpClients.custom()
			.setDefaultCredentialsProvider(credsProvider)
			.build()
	}

	def upload(graphUri: String, statements: Seq[Statement]): Unit = {
		val baos = new ByteArrayOutputStream()
		val writer = Rio.createWriter(RDFFormat.NTRIPLES, baos)
		writer.startRDF()
		statements.foreach(writer.handleStatement)
		writer.endRDF()

		val post = new HttpPost(endpointFor(graphUri))
		post.setEntity(new ByteArrayEntity(baos.toByteArray, ContentType.create("application/n-triples")))

		execute(post) { response =>
			val status = response.getStatusLine.getStatusCode
			if status >= 400 then
				throw new RuntimeException(s"Upload to $graphUri failed ($status): ${errorBody(response)}")
		}
	}

	def clear(graphUri: String): Boolean =
		execute(new HttpDelete(endpointFor(graphUri))) { response =>
			response.getStatusLine.getStatusCode match
				case 404 => false // Graph did not exist
				case status if status >= 400 =>
					throw new RuntimeException(s"Clearing graph $graphUri failed ($status): ${errorBody(response)}")
				case _ => true
		}

	def close(): Unit = httpClient.close()

	private def endpointFor(graphUri: String): String =
		s"$baseEndpoint?graph-uri=${URLEncoder.encode(graphUri, "UTF-8")}"

	private def execute[T](request: HttpUriRequest)(handler: HttpResponse => T): T =
		Using.resource(httpClient.execute(request))(handler)

	private def errorBody(response: HttpResponse): String =
		Option(response.getEntity).fold("")(EntityUtils.toString)

}
