import scala.language.unsafeNulls
import scala.util.Using

import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.file.Paths

import org.apache.http.HttpResponse
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.methods.{HttpDelete, HttpPost, HttpUriRequest}
import org.apache.http.entity.{ByteArrayEntity, ContentType}
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClients}
import org.apache.http.util.EntityUtils
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.repository.sail.{SailRepository, SailRepositoryConnection}
import org.eclipse.rdf4j.rio.{Rio, RDFFormat}
import org.eclipse.rdf4j.sail.lmdb.LmdbStore
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig
import org.slf4j.LoggerFactory

import se.lu.nateko.cp.meta.utils.rdf4j.asPlainScalaIterator
import tools.shared.config.{VirtuosoConfig, rdfStoragePath, virtuosoConfig}

/*
=== Description ===
Populates a Virtuoso triplestore from the triples already present in the local
RDF storage (LMDB) of this repository.

This is the LMDB-sourced counterpart of meta-unchained's
cli.TriplestorePopulator, which instead replays the Postgres RDF log into an
in-memory repository before uploading. Here the LMDB store is the source of
truth: every named graph (context) found in it is cleared in Virtuoso and then
re-uploaded in chunks via the SPARQL Graph Store HTTP Protocol.

Virtuoso connection details are read from application.conf under tools.virtuoso:

	tools.virtuoso {
		host = "http://localhost:8890"
		username = "dba"
		password = "dba"
	}
 */

private val ChunkSize = 100000
private val log = LoggerFactory.getLogger("devtools.populateVirtuoso")

@main def populateVirtuoso(): Unit = {
	val virtuosoConf = virtuosoConfig
	val graphUpdateEndpoint = s"${virtuosoConf.host}/sparql-graph-crud-auth"

	var totalWritten = 0L
	withRepoConn { conn =>
		Using.resource(new HttpGraphStore(graphUpdateEndpoint, virtuosoConf)) { graphStore =>
			val graphs = Using.resource(conn.getContextIDs()) { _.asPlainScalaIterator.toVector }
			log.info(s"Found ${graphs.size} named graphs in the local RDF storage")

			for graph <- graphs do {
				val graphUri = graph.stringValue

				log.info(s"$graphUri: clearing")
				graphStore.clear(graphUri)

				log.info(s"$graphUri: uploading statements")
				var written = 0
				Using.resource(conn.getStatements(null, null, null, false, graph)) { statements =>
					statements.asPlainScalaIterator.grouped(ChunkSize).foreach { chunk =>
						graphStore.upload(graphUri, chunk)
						written += chunk.size
						log.info(s"$graphUri: ${written / 1000}k statements written")
					}
				}
				totalWritten += written
				log.info(s"$graphUri: done ($written statements)")
			}
		}
	}
	log.info(s"All graphs ingested! Total triples: $totalWritten")
}

private def withRepo(callback: SailRepository => Any): Unit = {
	val storageDir = Paths.get(rdfStoragePath).resolve("lmdb")
	val sail = LmdbStore(storageDir.toFile, new LmdbStoreConfig())
	val repo = new SailRepository(sail)
	repo.init()
	try callback(repo) finally repo.shutDown()
}

private def withRepoConn(callback: SailRepositoryConnection => Any): Unit = {
	withRepo { repo =>
		Using.resource(repo.getConnection())(callback)
	}
}

private final class HttpGraphStore(baseEndpoint: String, conf: VirtuosoConfig) extends AutoCloseable {

	private val httpClient = {
		val credsProvider = new BasicCredentialsProvider()
		credsProvider.setCredentials(
			AuthScope.ANY,
			new UsernamePasswordCredentials(conf.username, conf.password)
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
			if status >= 400 then {
				throw new RuntimeException(s"Upload to $graphUri failed ($status): ${errorBody(response)}")
			}
		}
	}

	def clear(graphUri: String): Boolean = {
		execute(new HttpDelete(endpointFor(graphUri))) { response =>
			response.getStatusLine.getStatusCode match {
				case 404 => {
					log.info(s"$graphUri: did not exist")
					false
				}
				case status if status >= 400 => {
					throw new RuntimeException(s"Clearing graph $graphUri failed ($status): ${errorBody(response)}")
				}
				case _ => {
					log.info(s"$graphUri: cleared")
					true
				}
			}
		}
	}

	def close(): Unit = httpClient.close()

	private def endpointFor(graphUri: String): String =
		s"$baseEndpoint?graph-uri=${URLEncoder.encode(graphUri, "UTF-8")}"

	private def execute[T](request: HttpUriRequest)(handler: HttpResponse => T): T =
		Using.resource(httpClient.execute(request))(handler)

	private def errorBody(response: HttpResponse): String =
		Option(response.getEntity).fold("")(EntityUtils.toString)
}
