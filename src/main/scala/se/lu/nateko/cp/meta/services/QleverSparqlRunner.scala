package se.lu.nateko.cp.meta.services

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.resultio.helpers.QueryResultCollector
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONParser
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.rio.Rio
import org.eclipse.rdf4j.rio.helpers.StatementCollector
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlQuery, SparqlRunner}
import se.lu.nateko.cp.meta.services.sparql.QleverClient

import java.io.ByteArrayInputStream
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.IteratorHasAsScala

class QleverSparqlRunner(client: QleverClient)(using system: ActorSystem, mat: Materializer) extends SparqlRunner:
	private given scala.concurrent.ExecutionContext = system.dispatcher

	override def evaluateTupleQuery(q: SparqlQuery): CloseableIterator[BindingSet] =
		val bytes = fetchBytes(q.query, "application/sparql-results+json")
		val factory = SimpleValueFactory.getInstance()
		val parser = new SPARQLResultsJSONParser(factory)
		val collector = new QueryResultCollector()
		parser.setQueryResultHandler(collector)
		parser.parseQueryResult(new ByteArrayInputStream(bytes))
		new CloseableIterator.Wrap(collector.getBindingSets.iterator().asScala, () => ())

	override def evaluateGraphQuery(q: SparqlQuery): CloseableIterator[Statement] =
		val bytes = fetchBytes(q.query, "application/rdf+xml")
		val factory = SimpleValueFactory.getInstance()
		val collector = new StatementCollector()
		val parser = Rio.createParser(RDFFormat.RDFXML, factory)
		parser.setRDFHandler(collector)
		parser.parse(new ByteArrayInputStream(bytes), "")
		new CloseableIterator.Wrap(collector.getStatements.iterator().asScala, () => ())

	private def fetchBytes(query: String, acceptMime: String): Array[Byte] =
		val resp = Await.result(client.sparqlQuery(query, acceptMime), 65.seconds)
		val strict = Await.result(resp.entity.toStrict(60.seconds), 65.seconds)
		if !resp.status.isSuccess() then
			throw Exception(s"QLever query failed with ${resp.status}: ${strict.data.utf8String}")
		strict.data.toArray

end QleverSparqlRunner
