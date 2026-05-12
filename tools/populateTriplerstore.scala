import scala.language.unsafeNulls
import scala.util.Using

import org.slf4j.LoggerFactory
import org.eclipse.rdf4j.model.Statement
import meta.tools.replayRdfLog

private val ChunkSize = 100000

private val log = LoggerFactory.getLogger("tools.populateTriplestore")

/*
	Populate external triplestore using RDF Graph Update protocol
	NOTE: Requires large amounts of RAM, which means you most likely need:
		export SBT_OPTS="-Xmx12G -Xss2M"
*/
@main def populateTriplestore(args: String*): Unit = {
	import se.lu.nateko.cp.meta.{ConfigLoader, MetaDb}
	import se.lu.nateko.cp.meta.utils.rdf4j.asPlainScalaIterator
	import org.eclipse.rdf4j.model.impl.SimpleValueFactory

	val config = ConfigLoader.default
	val virtuosoConf = config.virtuoso
	val factory = SimpleValueFactory.getInstance()

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
		val graphUri = conf.writeContext.toString
		val memRepo = replayRdfLog(config.rdfLog, factory, logName)

		log.info(s"$graphUri: clearing")
		if (graphStore.clear(graphUri)) {
			log.info(s"$graphUri: cleared")
		} else {
			log.info(s"$graphUri: did not exist")
		}

		log.info(s"$logName: uploading final statements")
		var written = 0
		Using.resource(memRepo.getConnection) { conn =>
			Using.resource(conn.getStatements(null, null, null)) { stmts =>
				val statements = stmts.asPlainScalaIterator
				statements.grouped(ChunkSize).foreach { chunk =>
					graphStore.upload(graphUri, chunk)
					written += chunk.size
					log.info(s"$logName: ${written / 1000}k statements written")
				}
			}
		}
		memRepo.shutDown()
		log.info(s"Ingesting from RDF log $logName done!")
	}
	graphStore.close()
	log.info("All graphs ingested!")
}

private final class HttpGraphStore(baseEndpoint: String, username: String, password: String) extends AutoCloseable {
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
		val outStream = new ByteArrayOutputStream()
		val writer = Rio.createWriter(RDFFormat.NTRIPLES, outStream)
		writer.startRDF()
		statements.foreach(writer.handleStatement)
		writer.endRDF()

		val post = new HttpPost(endpointFor(graphUri))
		post.setEntity(new ByteArrayEntity(outStream.toByteArray, ContentType.create("application/n-triples")))

		execute(post) { response =>
			val status = response.getStatusLine.getStatusCode
			if (status >= 400) {
				throw new RuntimeException(s"Upload to $graphUri failed ($status): ${errorBody(response)}")
			}
		}
	}

	def clear(graphUri: String): Boolean = {
		execute(new HttpDelete(endpointFor(graphUri))) { response =>
			response.getStatusLine.getStatusCode match {
				case 404 => false // Graph did not exist
				case status if status >= 400 =>
					throw new RuntimeException(s"Clearing graph $graphUri failed ($status): ${errorBody(response)}")
				case _ => true
			}
		}
	}

	def close(): Unit = httpClient.close()

	private def endpointFor(graphUri: String): String = {
		s"$baseEndpoint?graph-uri=${URLEncoder.encode(graphUri, "UTF-8")}"
	}

	private def execute[T](request: HttpUriRequest)(handler: HttpResponse => T): T = {
		Using.resource(httpClient.execute(request))(handler)
	}

	private def errorBody(response: HttpResponse): String = {
		Option(response.getEntity).fold("")(EntityUtils.toString)
	}
}
