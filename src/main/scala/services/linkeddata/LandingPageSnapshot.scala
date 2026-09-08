package se.lu.nateko.cp.meta.services.linkeddata

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.{IRI, Resource, Value, ValueFactory}
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.rio.helpers.NTriplesUtil
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.instanceserver.{RdfStatement, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.asCloseableIterator

import scala.util.{Try, Using}

private[linkeddata] object LandingPageSnapshot:
	private final case class Entry(statement: RdfStatement, context: Option[IRI])

	private final class Connection(
		entries: IndexedSeq[Entry],
		val factory: ValueFactory,
		val primaryContext: IRI = null,
		val readContexts: Seq[IRI] = Nil
	) extends TriplestoreConnection:

		def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[RdfStatement] =
			CloseableIterator.Wrap(
				entries.iterator.collect:
					case Entry(statement, context)
						if visible(context) &&
							(subject == null || statement.subject == subject) &&
							(predicate == null || statement.predicate == predicate) &&
							(obj == null || statement.obj == obj) => statement,
				() => ()
			)

		def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean =
			getStatements(subject, predicate, obj).hasNext

		def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection =
			Connection(entries, factory, primary, read)

		def close(): Unit = ()

		private def visible(context: Option[IRI]): Boolean =
			readContexts.isEmpty || context.isEmpty || context.exists(readContexts.contains)

	def fetch(root: IRI, repo: Repository, metaVocab: CpmetaVocab): Try[TriplestoreConnection] = Using.Manager: use =>
		val conn = use(repo.getConnection())
		val query = conn.prepareTupleQuery(QueryLanguage.SPARQL, queryString(metaVocab))
		query.setBinding("resource", root)
		val result = use(query.evaluate().asCloseableIterator)
		val entries = result.flatMap: bindings =>
			(bindings.getValue("s"), bindings.getValue("p"), bindings.getValue("o")) match
				case (subject: Resource, predicate: IRI, obj: Value) =>
					val context = bindings.getValue("g") match
						case iri: IRI => Some(iri)
						case _ => None
					Some(Entry(RdfStatement(subject, predicate, obj), context))
				case _ => None
		Connection(entries.toIndexedSeq, repo.getValueFactory)

	private def queryString(metaVocab: CpmetaVocab): String =
		def iri(value: IRI) = NTriplesUtil.toNTriplesString(value)

		val excluded = Seq(RDF.TYPE, metaVocab.dcterms.hasPart).map(iri).mkString(" | ")
		val inverse = Seq(
			metaVocab.isNextVersionOf,
			metaVocab.dcterms.hasPart,
			metaVocab.atOrganization,
			metaVocab.ssn.hasDeployment
		).map(value => s"^${iri(value)}").mkString(" | ")
		val traversal = s"(!($excluded) | $inverse)*"
		val hasPart = iri(metaVocab.dcterms.hasPart)

		s"""SELECT DISTINCT ?s ?p ?o ?g
		|WHERE {
		|	{
		|		{ BIND(?resource AS ?s) }
		|		UNION { ?resource $traversal ?s . }
		|		UNION { GRAPH ?pathGraph { ?resource $traversal ?s . } }
		|		UNION { ?resource $hasPart ?s . }
		|		UNION { GRAPH ?pathGraph { ?resource $hasPart ?s . } }
		|	}
		|	{
		|		{
		|			?s ?p ?o .
		|			FILTER NOT EXISTS { GRAPH ?g { ?s ?p ?o } }
		|		}
		|		UNION { GRAPH ?g { ?s ?p ?o } }
		|	}
		|}""".stripMargin
