import scala.language.unsafeNulls

import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.{ConfigLoader, MetaDb}
import se.lu.nateko.cp.meta.persistence.postgres.PostgresRdfLog
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.rio.{Rio, RDFFormat}
import org.apache.http.auth.{AuthScope, UsernamePasswordCredentials}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.ByteArrayEntity
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.{BasicCredentialsProvider, HttpClients}
import org.apache.http.util.EntityUtils
import java.net.URLEncoder
import java.io.ByteArrayOutputStream

private val log = LoggerFactory.getLogger("tools.populateTriplestore")

private val ChunkSize = 100000

@main def populateTriplestore(args: String*): Unit =
	val config = ConfigLoader.default
	val rdfConf = config.rdfStorage
	given factory: SimpleValueFactory = SimpleValueFactory.getInstance()

	val credsProvider = new BasicCredentialsProvider()
	credsProvider.setCredentials(
		AuthScope.ANY,
		new UsernamePasswordCredentials(rdfConf.username, rdfConf.password)
	)
	val httpClient = HttpClients.custom()
		.setDefaultCredentialsProvider(credsProvider)
		.build()

	val allConfs = MetaDb.getAllInstanceServerConfigs(config.instanceServers)
	val selectedConfs = args.headOption.fold(allConfs): id =>
		allConfs.get(id).map(id -> _).toMap

	for {
		(_id, conf) <- selectedConfs
		logName <- conf.logName
	} do {
		val rdfLog = PostgresRdfLog(logName, config.rdfLog, factory)
		val graphUri = conf.writeContext.toString
		val endpoint = s"${rdfConf.updateEndpoint}?graph-uri=${URLEncoder.encode(graphUri, "UTF-8")}"

		var written = 0
		rdfLog.updates.grouped(ChunkSize).foreach(chunk =>
			val baos = new ByteArrayOutputStream()
			val writer = Rio.createWriter(RDFFormat.NTRIPLES, baos)
			writer.startRDF()
			chunk.foreach(st => writer.handleStatement(st.statement))
			writer.endRDF()

			val post = new HttpPost(endpoint)
			post.setEntity(new ByteArrayEntity(baos.toByteArray, ContentType.create("application/n-triples")))
			val response = httpClient.execute(post)
			try
				val status = response.getStatusLine.getStatusCode
				if status >= 400 then
					val body = EntityUtils.toString(response.getEntity)
					throw new RuntimeException(s"Upload to $graphUri failed ($status): $body")
			finally
				EntityUtils.consume(response.getEntity)

			written += chunk.size
			log.info(s"$logName: ${written / 1000}k statements written")
		)
		log.info(s"Ingesting from RDF log $logName done!")
	}
	httpClient.close()
	println(s"ALL DONE!")
