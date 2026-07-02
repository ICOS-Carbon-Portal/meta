import scala.language.unsafeNulls
import scala.collection.mutable
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
import org.eclipse.rdf4j.model.{Resource, Statement}
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
// Statements stored without a named graph (rdf4j's default/null context) are not
// enumerated by getContextIDs(); they are re-homed into this named graph in Virtuoso.
private val DefaultContextGraph = "fromdefault"
private val log = LoggerFactory.getLogger("devtools.populateVirtuoso")

@main def populateVirtuoso(): Unit = {
	val virtuosoConf = virtuosoConfig
	val graphUpdateEndpoint = s"${virtuosoConf.host}/sparql-graph-crud-auth"
	val sparqlEndpoint = s"${virtuosoConf.host}/sparql-auth"

	var totalWritten = 0L
	var totalVerified = 0L
	var anyMismatch = false
	withRepoConn { conn =>
		Using.resource(new HttpGraphStore(graphUpdateEndpoint, sparqlEndpoint, virtuosoConf)) { graphStore =>
			// Enumerate the source contexts. getContextIDs() is NOT reliable on the LMDB
			// store: it can omit contexts that actually hold statements (observed here: 3
			// graphs / 1531 statements missing). Relying on it would silently skip whole
			// graphs, so derive the authoritative context set from a full scan instead.
			val declared = Using.resource(conn.getContextIDs()) { _.asPlainScalaIterator.toVector }
			val contexts = mutable.LinkedHashSet.from(declared)
			var nullContextSeen = false
			log.info("Scanning all statements to enumerate contexts (getContextIDs is unreliable)...")
			Using.resource(conn.getStatements(null, null, null, false)) { statements =>
				statements.asPlainScalaIterator.foreach { st =>
					val ctx = st.getContext
					if ctx == null then nullContextSeen = true else contexts.add(ctx)
				}
			}
			val undeclared = contexts.filterNot(declared.contains).toVector
			log.info(s"getContextIDs() reported ${declared.size} contexts; full scan found ${contexts.size} non-null contexts")
			if undeclared.nonEmpty then {
				log.warn(
					s"${undeclared.size} context(s) are missing from getContextIDs() and would have been " +
					s"silently skipped: ${undeclared.map(_.stringValue).mkString(", ")}"
				)
			}

			// Uploads the statements in the given source contexts into the given Virtuoso
			// graph, then verifies (check #2) that Virtuoso ends up holding exactly the
			// source count. Fewer => statements were silently dropped; more => blank nodes
			// were split across chunks (or the graph was not cleared).
			def ingestGraph(targetGraphUri: String, sourceContexts: Array[Resource]): Unit = {
				log.info(s"$targetGraphUri: clearing")
				graphStore.clear(targetGraphUri)

				log.info(s"$targetGraphUri: uploading statements")
				var written = 0
				Using.resource(conn.getStatements(null, null, null, false, sourceContexts*)) { statements =>
					statements.asPlainScalaIterator.grouped(ChunkSize).foreach { chunk =>
						graphStore.upload(targetGraphUri, chunk)
						written += chunk.size
						log.info(s"$targetGraphUri: ${written / 1000}k statements written")
					}
				}
				totalWritten += written

				val sourceCount = conn.size(sourceContexts*)
				val virtuosoCount = graphStore.count(targetGraphUri)
				totalVerified += virtuosoCount
				if virtuosoCount != sourceCount then {
					anyMismatch = true
					log.error(
						s"$targetGraphUri: COUNT MISMATCH — source has $sourceCount, Virtuoso has $virtuosoCount " +
						s"(delta ${virtuosoCount - sourceCount})"
					)
				} else {
					log.info(s"$targetGraphUri: verified $virtuosoCount statements in Virtuoso")
				}
				log.info(s"$targetGraphUri: done ($written statements)")
			}

			val sourceTotalAll = conn.size()

			for ctx <- contexts do ingestGraph(ctx.stringValue, Array[Resource](ctx))

			// Check #1: any genuine default/null-context statements (a single null in the
			// contexts array selects the default context specifically) are re-homed into an
			// explicit named graph rather than dropped. Currently this is empty, but keep
			// the guard so such statements can never be silently lost.
			if nullContextSeen then {
				log.info(s"Uploading default/null-context statements to the '$DefaultContextGraph' graph")
				ingestGraph(DefaultContextGraph, Array[Resource](null))
			}

			// Check #5: what Virtuoso holds across all uploaded graphs must equal the full
			// source total (named graphs plus the re-homed default context).
			log.info(
				s"All graphs ingested! Sent: $totalWritten; verified in Virtuoso: $totalVerified; " +
				s"source total: $sourceTotalAll"
			)
			if totalVerified != sourceTotalAll then {
				anyMismatch = true
				log.error(
					s"TOTAL MISMATCH — source holds $sourceTotalAll statements " +
					s"but Virtuoso holds $totalVerified (delta ${totalVerified - sourceTotalAll})"
				)
			}
			if anyMismatch then {
				throw new RuntimeException("Verification failed: Virtuoso does not match the local RDF storage (see errors above)")
			} else {
				log.info("Verification passed: Virtuoso matches the local RDF storage")
			}
		}
	}
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

private final class HttpGraphStore(baseEndpoint: String, sparqlEndpoint: String, conf: VirtuosoConfig) extends AutoCloseable {

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

	/** Number of triples Virtuoso currently holds in the given named graph. */
	def count(graphUri: String): Long = {
		val query = s"SELECT (COUNT(*) AS ?c) FROM <$graphUri> WHERE { ?s ?p ?o }"
		val post = new HttpPost(sparqlEndpoint)
		post.setEntity(new ByteArrayEntity(
			s"query=${URLEncoder.encode(query, "UTF-8")}".getBytes("UTF-8"),
			ContentType.create("application/x-www-form-urlencoded")
		))
		post.setHeader("Accept", "text/csv")

		execute(post) { response =>
			val status = response.getStatusLine.getStatusCode
			val body = Option(response.getEntity).fold("")(EntityUtils.toString)
			if status >= 400 then {
				throw new RuntimeException(s"Count query for $graphUri failed ($status): $body")
			}
			parseCsvCount(graphUri, body)
		}
	}

	// A COUNT SPARQL result in CSV is a header line ("c") followed by the value line.
	private def parseCsvCount(graphUri: String, csv: String): Long = {
		val lines = csv.split("\\R").iterator.map(_.trim).filter(_.nonEmpty).toVector
		lines.lift(1) match {
			case Some(value) => value.stripPrefix("\"").stripSuffix("\"").toLong
			case None => throw new RuntimeException(s"Unexpected count response for $graphUri: '$csv'")
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
