package se.lu.nateko.cp.meta.instanceserver

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.eclipse.rdf4j.model.{BNode, IRI, Literal, Resource, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.query.resultio.helpers.QueryResultCollector
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONParser
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlQuery, SparqlRunner}
import se.lu.nateko.cp.meta.services.sparql.QleverClient
import spray.json.{JsBoolean, JsonParser}

import java.io.ByteArrayInputStream
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.{IterableHasAsScala, IteratorHasAsScala}
import scala.util.Try

class QleverInstanceServer(
	client: QleverClient,
	val readContexts: Seq[IRI],
	val writeContext: IRI
)(using system: ActorSystem, mat: Materializer) extends InstanceServer:

	private val vf = SimpleValueFactory.getInstance()
	private given scala.concurrent.ExecutionContext = system.dispatcher

	override def factory: ValueFactory = vf

	override def makeNewInstance(prefix: IRI): IRI =
		vf.createIRI(prefix.stringValue.stripSuffix("/") + "/", UUID.randomUUID.toString)

	override def withContexts(read: Seq[IRI], write: IRI): InstanceServer =
		new QleverInstanceServer(client, read, write)

	override def getConnection(): TriplestoreConnection & SparqlRunner =
		new QleverTriplestoreConnection(client, writeContext, readContexts)

	override def getStatements(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): CloseableIterator[Statement] =
		val conn = getConnection()
		try conn.getStatements(subject.orNull, predicate.orNull, obj.orNull)
		finally conn.close()

	override def applyAll(updates: Seq[RdfUpdate])(cotransact: => Unit = ()): Try[Unit] = Try:
		if updates.nonEmpty then
			cotransact
			val groups = buildConsecutiveGroups(updates)
			val updateStr = groups.map: (isAssert, stmts) =>
				val verb = if isAssert then "INSERT DATA" else "DELETE DATA"
				val triples = stmts.map(tripleStr).mkString(" ")
				s"$verb { GRAPH <${writeContext.stringValue}> { $triples } }"
			.mkString(" ; ")
			Await.result(client.sparqlUpdate(updateStr), 60.seconds)

	override def shutDown(): Unit = ()

	private def buildConsecutiveGroups(updates: Seq[RdfUpdate]): List[(Boolean, Vector[Statement])] =
		updates.foldLeft(List.empty[(Boolean, Vector[Statement])]):
			case ((isAssert, stmts) :: rest, update) if isAssert == update.isAssertion =>
				(isAssert, stmts :+ update.statement) :: rest
			case (groups, update) =>
				(update.isAssertion, Vector(update.statement)) :: groups
		.reverse

end QleverInstanceServer


class QleverTriplestoreConnection(
	client: QleverClient,
	val primaryContext: IRI,
	val readContexts: Seq[IRI]
)(using system: ActorSystem, mat: Materializer) extends TriplestoreConnection, SparqlRunner:

	private val vf = SimpleValueFactory.getInstance()
	private given scala.concurrent.ExecutionContext = system.dispatcher

	override def factory: ValueFactory = vf

	override def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection =
		new QleverTriplestoreConnection(client, primary, read)

	override def close(): Unit = ()

	override def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement] =
		val query = buildSelectQuery(readContexts, subject, predicate, obj)
		val bytes = fetchQueryBytes(query, "application/sparql-results+json")
		val parser = new SPARQLResultsJSONParser(vf)
		val collector = new QueryResultCollector()
		parser.setQueryResultHandler(collector)
		parser.parseQueryResult(new ByteArrayInputStream(bytes))
		val stmts = collector.getBindingSets.asScala.map: bs =>
			val s = Option(subject).getOrElse(bs.getValue("s").asInstanceOf[Resource])
			val p = Option(predicate).getOrElse(bs.getValue("p").asInstanceOf[IRI])
			val o = Option(obj).getOrElse(bs.getValue("o"))
			vf.createStatement(s, p, o)
		new CloseableIterator.Wrap(stmts.iterator, () => ())

	override def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean =
		val query = buildAskQuery(readContexts, subject, predicate, obj)
		val bytes = fetchQueryBytes(query, "application/sparql-results+json")
		val body = new String(bytes, "UTF-8")
		JsonParser(body).asJsObject.fields.get("boolean") match
			case Some(JsBoolean(v)) => v
			case _ => throw Exception(s"Unexpected ASK response: $body")

	override def evaluateTupleQuery(q: SparqlQuery): CloseableIterator[BindingSet] =
		val bytes = fetchQueryBytes(q.query, "application/sparql-results+json")
		val parser = new SPARQLResultsJSONParser(vf)
		val collector = new QueryResultCollector()
		parser.setQueryResultHandler(collector)
		parser.parseQueryResult(new ByteArrayInputStream(bytes))
		new CloseableIterator.Wrap(collector.getBindingSets.iterator().asScala, () => ())

	override def evaluateGraphQuery(q: SparqlQuery): CloseableIterator[Statement] =
		import org.eclipse.rdf4j.rio.{RDFFormat, Rio}
		import org.eclipse.rdf4j.rio.helpers.StatementCollector
		val bytes = fetchQueryBytes(q.query, "application/rdf+xml")
		val collector = new StatementCollector()
		val parser = Rio.createParser(RDFFormat.RDFXML, vf)
		parser.setRDFHandler(collector)
		parser.parse(new ByteArrayInputStream(bytes), "")
		new CloseableIterator.Wrap(collector.getStatements.iterator().asScala, () => ())

	private def fetchQueryBytes(query: String, acceptMime: String): Array[Byte] =
		val resp = Await.result(client.sparqlQuery(query, acceptMime), 65.seconds)
		val strict = Await.result(resp.entity.toStrict(60.seconds), 65.seconds)
		if !resp.status.isSuccess() then
			throw Exception(s"QLever query failed with ${resp.status}: ${strict.data.utf8String}")
		strict.data.toArray

end QleverTriplestoreConnection


private def sparqlTerm(v: Value): String = v match
	case iri: IRI => s"<${iri.stringValue}>"
	case lit: Literal => lit.toString()
	case bnode: BNode => s"_:${bnode.getID}"

private def tripleStr(s: Statement): String =
	s"${sparqlTerm(s.getSubject)} <${s.getPredicate.stringValue}> ${sparqlTerm(s.getObject)} ."

private def buildSelectQuery(
	readContexts: Seq[IRI],
	s: IRI | Null, p: IRI | Null, o: Value | Null
): String =
	val sExpr = if s == null then "?s" else s"(<${s.stringValue}> AS ?s)"
	val pExpr = if p == null then "?p" else s"(<${p.stringValue}> AS ?p)"
	val oExpr = if o == null then "?o" else s"(${sparqlTerm(o)} AS ?o)"
	val sPattern = if s == null then "?s" else s"<${s.stringValue}>"
	val pPattern = if p == null then "?p" else s"<${p.stringValue}>"
	val oPattern = if o == null then "?o" else sparqlTerm(o)
	val triple = s"$sPattern $pPattern $oPattern"
	val graphPart = readContexts match
		case Nil => triple
		case ctxs => ctxs.map(ctx => s"GRAPH <${ctx.stringValue}> { $triple }").mkString(" UNION ")
	s"SELECT $sExpr $pExpr $oExpr WHERE { $graphPart }"

private def buildAskQuery(
	readContexts: Seq[IRI],
	s: IRI | Null, p: IRI | Null, o: Value | Null
): String =
	val sPattern = if s == null then "?s" else s"<${s.stringValue}>"
	val pPattern = if p == null then "?p" else s"<${p.stringValue}>"
	val oPattern = if o == null then "?o" else sparqlTerm(o)
	val triple = s"$sPattern $pPattern $oPattern"
	val graphPart = readContexts match
		case Nil => triple
		case ctxs => ctxs.map(ctx => s"GRAPH <${ctx.stringValue}> { $triple }").mkString(" UNION ")
	s"ASK WHERE { $graphPart }"
