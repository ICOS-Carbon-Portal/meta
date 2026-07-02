import scala.language.unsafeNulls
import scala.collection.mutable
import scala.util.Using

import java.nio.file.Paths

import org.eclipse.rdf4j.model.{BNode, IRI, Literal, Resource, Statement, Value}
import org.eclipse.rdf4j.query.QueryLanguage
import org.eclipse.rdf4j.repository.Repository
import org.eclipse.rdf4j.repository.RepositoryConnection
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.repository.sparql.SPARQLRepository
import org.eclipse.rdf4j.sail.lmdb.LmdbStore
import org.eclipse.rdf4j.sail.lmdb.config.LmdbStoreConfig
import org.slf4j.LoggerFactory

import se.lu.nateko.cp.meta.utils.rdf4j.asPlainScalaIterator
import tools.shared.config.{rdfStoragePath, virtuosoConfig}

/*
=== Description ===
Locates the statement(s) that differ between the local RDF storage (LMDB) and
Virtuoso, e.g. the single triple behind a "TOTAL MISMATCH ... delta -1".

Usage:
    tools/runMain findMismatch [<graphUri> ...]

With no arguments it enumerates every context (via a full scan, since
getContextIDs() is unreliable), finds the graph(s) whose source count differs
from Virtuoso's, and then, for each, prints the exact triples present on one
side but not the other. Pass explicit graph URIs to skip enumeration.

Comparison is by canonical N-Triples form, so Virtuoso's literal
canonicalization (e.g. "1.0" -> "1.0E0", dateTime reformatting) will show up as
matching source-only / virtuoso-only pairs; a true drop shows up as an
unmatched source-only triple.
 */

private val log = LoggerFactory.getLogger("devtools.findMismatch")
private val MaxPrint = 200

@main def findMismatch(graphArgs: String*): Unit = {
	val vconf = virtuosoConfig
	val vrepo = new SPARQLRepository(s"${vconf.host}/sparql")
	vrepo.init()
	try withRepoConn { conn =>
		Using.resource(vrepo.getConnection()) { vconn =>

			val graphs: Vector[String] =
				if graphArgs.nonEmpty then graphArgs.toVector
				else {
					log.info("Scanning all statements to enumerate contexts...")
					val ctxs = mutable.LinkedHashSet.empty[Resource]
					Using.resource(conn.getStatements(null, null, null, false)) { statements =>
						statements.asPlainScalaIterator.foreach { st =>
							val ctx = st.getContext
							if ctx != null then ctxs.add(ctx)
						}
					}
					val mism = ctxs.toVector.map(_.stringValue).filter { g =>
						val src = conn.size(sf.createIRI(g))
						val virt = virtuosoCount(vconn, g)
						if src != virt then {
							log.warn(s"COUNT MISMATCH: $g — source $src, Virtuoso $virt (delta ${virt - src})")
							true
						} else false
					}
					if mism.isEmpty then log.info("No per-graph count mismatch found") else
						log.info(s"${mism.size} graph(s) mismatch; drilling into each")
					mism
				}

			for graph <- graphs do drill(conn, vconn, graph)
		}
	} finally vrepo.shutDown()
}

private val sf = org.eclipse.rdf4j.model.impl.SimpleValueFactory.getInstance()

private def virtuosoCount(vconn: RepositoryConnection, graph: String): Long = {
	val q = vconn.prepareTupleQuery(
		QueryLanguage.SPARQL,
		s"SELECT (COUNT(*) AS ?c) WHERE { GRAPH <$graph> { ?s ?p ?o } }"
	)
	Using.resource(q.evaluate()) { res =>
		if res.hasNext then res.next().getValue("c").stringValue.toLong else 0L
	}
}

private def drill(conn: RepositoryConnection, vconn: RepositoryConnection, graph: String): Unit = {
	log.info(s"$graph: loading source and Virtuoso triples for comparison...")

	val source = mutable.HashMap.empty[String, Int]
	Using.resource(conn.getStatements(null, null, null, false, sf.createIRI(graph))) { statements =>
		statements.asPlainScalaIterator.foreach(st => bump(source, triple(st.getSubject, st.getPredicate, st.getObject)))
	}

	val virtuoso = mutable.HashMap.empty[String, Int]
	val gq = vconn.prepareGraphQuery(
		QueryLanguage.SPARQL,
		s"CONSTRUCT { ?s ?p ?o } WHERE { GRAPH <$graph> { ?s ?p ?o } }"
	)
	Using.resource(gq.evaluate()) { res =>
		res.asPlainScalaIterator.foreach(st => bump(virtuoso, triple(st.getSubject, st.getPredicate, st.getObject)))
	}

	val sourceOnly = multisetDiff(source, virtuoso)
	val virtuosoOnly = multisetDiff(virtuoso, source)
	log.info(
		s"$graph: source ${source.values.sum} triples, Virtuoso ${virtuoso.values.sum} triples; " +
		s"source-only ${sourceOnly.map(_._2).sum}, virtuoso-only ${virtuosoOnly.map(_._2).sum}"
	)
	printSection(s"$graph: in SOURCE but not in Virtuoso", sourceOnly)
	printSection(s"$graph: in Virtuoso but not in SOURCE", virtuosoOnly)
}

private def triple(s: Resource, p: IRI, o: Value): String =
	s"${termKey(s)} ${termKey(p)} ${termKey(o)}"

// Canonical, comparison-only rendering of an rdf4j term. Both sides yield rdf4j
// Values, so identical terms render identically; datatype and language are kept
// so a plain and a typed literal with the same lexical form are not conflated.
private def termKey(v: Value): String = v match {
	case iri: IRI => s"<${iri.stringValue}>"
	case bn: BNode => s"_:${bn.getID}"
	case lit: Literal =>
		val lang = lit.getLanguage
		if lang.isPresent then s""""${lit.getLabel}"@${lang.get}"""
		else s""""${lit.getLabel}"^^<${lit.getDatatype.stringValue}>"""
	case other => other.stringValue
}

private def bump(m: mutable.HashMap[String, Int], key: String): Unit =
	m.update(key, m.getOrElse(key, 0) + 1)

private def multisetDiff(a: mutable.HashMap[String, Int], b: mutable.HashMap[String, Int]): Vector[(String, Int)] =
	a.iterator.flatMap { case (k, ca) =>
		val extra = ca - b.getOrElse(k, 0)
		if extra > 0 then Some(k -> extra) else None
	}.toVector.sortBy(_._1)

private def printSection(title: String, diff: Vector[(String, Int)]): Unit = {
	val n = diff.map(_._2).sum
	if n == 0 then log.info(s"$title: none")
	else {
		log.warn(s"$title: $n triple(s)" + (if n > MaxPrint then s" (showing first $MaxPrint)" else ""))
		diff.iterator.flatMap { case (line, c) => Iterator.fill(c)(line) }.take(MaxPrint).foreach(l => println(s"  $l"))
	}
}

private def withRepo(callback: Repository => Any): Unit = {
	val storageDir = Paths.get(rdfStoragePath).resolve("lmdb")
	val sail = LmdbStore(storageDir.toFile, new LmdbStoreConfig())
	val repo = new SailRepository(sail)
	repo.init()
	try callback(repo) finally repo.shutDown()
}

private def withRepoConn(callback: RepositoryConnection => Any): Unit = {
	withRepo { repo =>
		Using.resource(repo.getConnection())(callback)
	}
}
