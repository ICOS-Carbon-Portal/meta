package se.lu.nateko.cp.meta.cli

import scala.language.unsafeNulls
import scala.util.Using

import java.io.ByteArrayOutputStream
import java.net.URLEncoder

import org.apache.http.HttpResponse
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.methods.{HttpDelete, HttpPost, HttpUriRequest}
import org.apache.http.entity.{ByteArrayEntity, ContentType}
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClients}
import org.apache.http.util.EntityUtils
import org.eclipse.rdf4j.model.{Statement, ValueFactory}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.rio.{Rio, RDFFormat}
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.slf4j.LoggerFactory

import se.lu.nateko.cp.meta.{CpmetaConfig, MetaDb, RdflogConfig}
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import se.lu.nateko.cp.meta.utils.rdf4j.asPlainScalaIterator

object TriplestorePopulator {

	private val ChunkSize = 100000
	private val log = LoggerFactory.getLogger("TriplestorePopulator")

	def run(config: CpmetaConfig, onlyInstanceServer: Option[String]): Unit = {
		val virtuosoConf = config.virtuoso
		val factory = SimpleValueFactory.getInstance()

		val graphUpdateEndpoint = s"${virtuosoConf.host}/sparql-graph-crud-auth"
		val graphStore = new HttpGraphStore(graphUpdateEndpoint, virtuosoConf.username, virtuosoConf.password)

		val allConfs = MetaDb.getAllInstanceServerConfigs(config.instanceServers)
		val selectedConfs = onlyInstanceServer match
			case None     => allConfs
			case Some(id) => Map(id -> allConfs.get(id).get)

		var totalWritten = 0L
		for
			(_id, conf) <- selectedConfs
			logName <- conf.logName
		do
			val graphUri = conf.writeContext.toString
			val memRepo = replayRdfLog(config.rdfLog, factory, logName)

			log.info(s"$logName: clearing")
			graphStore.clear(graphUri)

			log.info(s"$logName: uploading final statements")
			var written = 0
			Using.resource(memRepo.getConnection) { conn =>
				Using.resource(conn.getStatements(null, null, null)) { statements =>
					statements.asPlainScalaIterator.grouped(ChunkSize).foreach { chunk =>
						graphStore.upload(graphUri, chunk)
						written += chunk.size
						log.info(s"$logName: ${written / 1000}k statements written")
					}
				}
			}
			memRepo.shutDown()
			totalWritten += written
			log.info(s"Ingesting from RDF log $logName done!")

		graphStore.close()
		log.info(s"All graphs ingested! Total triples: $totalWritten")
	}

	private def replayRdfLog(rdfLogConfig: RdflogConfig, factory: ValueFactory, logName: String): SailRepository = {
		val rdfLog = PostgresRdfLog(logName, rdfLogConfig, factory)
		log.info(s"$logName: replaying updates into in-memory repository")
		val memRepo = new SailRepository(new MemoryStore)
		memRepo.init()
		Using.resource(rdfLog.updates) { updates =>
			var replayed = 0
			Using.resource(memRepo.getConnection) { conn =>
				updates.foreach { update =>
					if update.isAssertion then conn.add(update.statement)
					else conn.remove(update.statement)
					replayed += 1
					if replayed % 100000 == 0 then
						log.info(s"$logName: ${replayed / 1000}k updates replayed")
				}
			}
			log.info(s"$logName: $replayed updates replayed")
		}
		memRepo
	}

	private final class HttpGraphStore(baseEndpoint: String, username: String, password: String) extends AutoCloseable {

		private val httpClient =
			val credsProvider = new BasicCredentialsProvider()
			credsProvider.setCredentials(
				AuthScope.ANY,
				new UsernamePasswordCredentials(username, password)
			)
			HttpClients.custom()
				.setDefaultCredentialsProvider(credsProvider)
				.build()

		def upload(graphUri: String, statements: Seq[Statement]): Unit =
			val outStream = new ByteArrayOutputStream()
			val writer = Rio.createWriter(RDFFormat.NTRIPLES, outStream)
			writer.startRDF()
			statements.foreach(writer.handleStatement)
			writer.endRDF()

			val post = new HttpPost(endpointFor(graphUri))
			post.setEntity(new ByteArrayEntity(outStream.toByteArray, ContentType.create("application/n-triples")))

			execute(post) { response =>
				val status = response.getStatusLine.getStatusCode
				if status >= 400 then
					throw new RuntimeException(s"Upload to $graphUri failed ($status): ${errorBody(response)}")
			}

		def clear(graphUri: String): Boolean =
			execute(new HttpDelete(endpointFor(graphUri))) { response =>
				response.getStatusLine.getStatusCode match
					case 404 =>
						log.info(s"$graphUri: did not exist")
						false
					case status if status >= 400 =>
						throw new RuntimeException(s"Clearing graph $graphUri failed ($status): ${errorBody(response)}")
					case _ =>
						log.info(s"$graphUri: cleared")
						true
			}

		def close(): Unit = httpClient.close()

		private def endpointFor(graphUri: String): String =
			s"$baseEndpoint?graph-uri=${URLEncoder.encode(graphUri, "UTF-8")}"

		private def execute[T](request: HttpUriRequest)(handler: HttpResponse => T): T =
			Using.resource(httpClient.execute(request))(handler)

		private def errorBody(response: HttpResponse): String =
			Option(response.getEntity).fold("")(EntityUtils.toString)
	}

}
