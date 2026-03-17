import scala.language.unsafeNulls
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.http.HTTPRepository
import org.eclipse.rdf4j.sail.lmdb.LmdbStore
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, Value, ValueFactory}
import java.nio.file.Paths
import org.slf4j.LoggerFactory
import scala.jdk.CollectionConverters.*

/*
=== Description ===
One-time migration script to bulk-transfer all triples from a local LMDB store
to a remote GraphDB repository. Named graph contexts are preserved.

=== Usage ===
./devtools/run.sh migrateToGraphDb <graphdb-server-url> <repository-id>

=== Example ===
./devtools/run.sh migrateToGraphDb http://localhost:7200 my-repository

=== Example configuration snippet for application.conf ===

	devtools {
		migrateToGraphDb {
			rdfStoragePath: "./someRdfStorageDir"
		}
	}

 */

private val log = LoggerFactory.getLogger("devtools.migrateToGraphDb")
private val batchSize = 10_000

private def sanitizeIriStr(raw: String): String =
	raw.flatMap(c =>
		if c <= '\u0020' || c == '\u007f' then f"%%${c.toInt}%02X"
		else c.toString
	)

private def sanitizeIri(vf: ValueFactory, iri: IRI): IRI =
	val raw = iri.stringValue()
	val encoded = sanitizeIriStr(raw)
	if encoded == raw then iri
	else
		log.warn(s"Encoding invalid IRI: $raw -> $encoded")
		vf.createIRI(encoded)

private def sanitizeValue(vf: ValueFactory, v: Value): Value = v match
	case iri: IRI => sanitizeIri(vf, iri)
	case other    => other

private def sanitizeStatement(vf: ValueFactory, stmt: Statement): Statement =
	val s = sanitizeValue(vf, stmt.getSubject()).asInstanceOf[Resource]
	val p = sanitizeIri(vf, stmt.getPredicate())
	val o = sanitizeValue(vf, stmt.getObject())
	val c = stmt.getContext()
	val sc = if c != null then sanitizeValue(vf, c).asInstanceOf[Resource] else null
	vf.createStatement(s, p, o, sc)

@main def migrateToGraphDb(args: String*) =
	if args.length < 2 then
		log.error("Usage: migrateToGraphDb <graphdb-server-url> <repository-id>")
		System.exit(1)

	val serverUrl = args(0)
	val repositoryId = args(1)

	val storageDir = Paths.get(devtools.config.rdfStoragePath).resolve("lmdb")
	log.info(s"Opening LMDB store at $storageDir")
	val lmdbRepo = new SailRepository(LmdbStore(storageDir.toFile, new LmdbStoreConfig()))

	log.info(s"Connecting to GraphDB at $serverUrl, repository '$repositoryId'")
	val graphdbRepo = new HTTPRepository(serverUrl, repositoryId)
	graphdbRepo.init()

	try
		val lmdbConn = lmdbRepo.getConnection()
		try
			val graphdbConn = graphdbRepo.getConnection()
			try
				val statements = lmdbConn.getStatements(null, null, null, false)
				var batch = List.newBuilder[org.eclipse.rdf4j.model.Statement]
				var batchCount = 0
				var total = 0

				val vf = lmdbConn.getValueFactory()
				while statements.hasNext() do
					batch += sanitizeStatement(vf, statements.next())
					batchCount += 1
					if batchCount >= batchSize then
						graphdbConn.begin()
						graphdbConn.add(batch.result().asJava)
						graphdbConn.commit()
						total += batchCount
						log.info(s"Transferred $total triples so far...")
						batch = List.newBuilder
						batchCount = 0

				val remaining = batch.result()
				if remaining.nonEmpty then
					graphdbConn.begin()
					graphdbConn.add(remaining.asJava)
					graphdbConn.commit()
					total += remaining.size

				log.info(s"Migration complete. Total triples transferred: $total")
			finally
				graphdbConn.close()
		finally
			lmdbConn.close()
	finally
		lmdbRepo.shutDown()
		graphdbRepo.shutDown()
