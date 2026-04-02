package se.lu.nateko.cp.meta.persistence

import scala.language.unsafeNulls

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.eclipse.rdf4j.model.{BNode, IRI, Literal, Statement, Value}
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.sparql.QleverClient

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{Try, Using}

object QleverUpdateLogIngester:

	val ChunkSize = 1000

	def ingest(
		updates: CloseableIterator[RdfUpdate],
		client: QleverClient,
		clearFirst: Boolean,
		writeContext: IRI
	)(using ActorSystem, Materializer): Try[Unit] =

		def sendChunk(chunk: Seq[RdfUpdate]): Try[Unit] = Try:
			if chunk.nonEmpty then
				val groups = toConsecutiveGroups(chunk)
				val updateStr = groups.map: (isAssert, stmts) =>
					val verb = if isAssert then "INSERT DATA" else "DELETE DATA"
					val triples = stmts.map(toNTripleStr).mkString(" ")
					s"$verb { GRAPH <${writeContext.stringValue}> { $triples } }"
				.mkString(" ; ")
				Await.result(client.sparqlUpdate(updateStr), 120.seconds)

		Using(updates): updates =>
			if clearFirst then
				Await.result(
					client.sparqlUpdate(s"CLEAR GRAPH <${writeContext.stringValue}>"),
					60.seconds
				)
			updates.sliding(ChunkSize, ChunkSize).foreach(chunk => sendChunk(chunk).get)

	private def toConsecutiveGroups(updates: Seq[RdfUpdate]): List[(Boolean, Vector[Statement])] =
		updates.foldLeft(List.empty[(Boolean, Vector[Statement])]):
			case ((isAssert, stmts) :: rest, update) if isAssert == update.isAssertion =>
				(isAssert, stmts :+ update.statement) :: rest
			case (groups, update) =>
				(update.isAssertion, Vector(update.statement)) :: groups
		.reverse

	private def toNTripleStr(s: Statement): String =
		s"${toSparqlTerm(s.getSubject)} <${s.getPredicate.stringValue}> ${toSparqlTerm(s.getObject)} ."

	private def toSparqlTerm(v: Value): String = v match
		case iri: IRI => s"<${iri.stringValue}>"
		case lit: Literal => lit.toString()
		case bnode: BNode => s"_:${bnode.getID}"

end QleverUpdateLogIngester
