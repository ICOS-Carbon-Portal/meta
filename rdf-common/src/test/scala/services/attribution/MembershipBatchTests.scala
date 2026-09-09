package se.lu.nateko.cp.meta.services.attribution

import scala.language.unsafeNulls
import java.net.URI
import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.{IRI, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.query.BindingSet
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.{CloseableIterator, RdfLens, SparqlRunner}
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, EnvriConfigs}
import se.lu.nateko.cp.meta.instanceserver.{Rdf4jTriplestoreConnection, RdfStatement, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab, Rdf4jSparqlRunner}
import se.lu.nateko.cp.meta.utils.rdf4j.*

class MembershipBatchTests extends AnyFunSpec:
	for count <- Seq(0, 1, 30) do
		it(s"matches the original reader with $count memberships in one query, excluding deployment candidates and other graphs"):
			val repo = Loading.emptyInMemory
			val db = repo.getConnection()
			val vf = repo.getValueFactory
			def iri(s: String) = vf.createIRI("https://example.org/", s)
			val graph = iri("graph")
			val otherGraph = iri("otherGraph")
			val org = iri("org")
			val v = CpmetaVocab(vf)
			given EnvriConfigs = Map(Envri.ICOS -> EnvriConfig(
				authHost = "example.org", dataHost = "example.org", metaHost = "example.org",
				dataItemPrefix = URI("https://example.org/"), metaItemPrefix = URI("https://example.org/"), defaultTimezoneId = "UTC"
			))
			val reader = AttributionProvider(CpVocab(vf), v)
			try
				for i <- 0 until count do
					val m = iri(s"m$i")
					val p = iri(s"p$i")
					db.add(m, v.atOrganization, org, graph)
					db.add(p, v.hasMembership, m, graph)
					db.add(p, v.hasFirstName, vf.createLiteral(s"First$i"), graph)
					db.add(p, v.hasLastName, vf.createLiteral("Last"), graph)
					// Missing role on one member exercises validation parity.
					if i != 2 then db.add(m, v.hasRole, iri("role"), graph)
					db.add(m, v.hasAttributionWeight, vf.createLiteral(i), graph)
					db.add(p, v.hasFirstName, vf.createLiteral("Wrong graph"), otherGraph)
				for i <- 0 until 100 do db.add(iri(s"deployment$i"), v.atOrganization, org, graph)
				db.add(iri("role"), RDFS.LABEL, vf.createLiteral("Researcher"), graph)
				val local = Rdf4jTriplestoreConnection(graph, Seq(graph), db)
				val expected = reader.getMemberships(org.toJava)(using RdfLens.metaLens(graph.toJava, Seq(graph.toJava))(using local))
				var queries = 0
				val runner = new SparqlRunner:
					def evaluateGraphQuery(q: String): CloseableIterator[Statement] =
						queries += 1
						Rdf4jSparqlRunner(repo).evaluateGraphQuery(q)
					def evaluateTupleQuery(q: String): CloseableIterator[BindingSet] = throw AssertionError("Unexpected SELECT")
				val noReads = new TriplestoreConnection:
					val primaryContext = graph
					val readContexts = Seq(graph)
					val factory: ValueFactory = vf
					def withContexts(p: IRI, r: Seq[IRI]): TriplestoreConnection = this
					def close(): Unit = ()
					def getStatements(s: IRI | Null, p: IRI | Null, o: Value | Null): CloseableIterator[RdfStatement] = throw AssertionError("Remote property read")
					def hasStatement(s: IRI | Null, p: IRI | Null, o: Value | Null): Boolean = throw AssertionError("Remote ASK")
				val actual = reader.getMembershipsBatched(org.toJava)(using RdfLens.metaLens(graph.toJava, Seq(graph.toJava))(using noReads), runner)
				assert(queries == 1)
				assert(actual.result.map(_.toSet) == expected.result.map(_.toSet))
				assert(actual.errors.toSet == expected.errors.toSet)
			finally
				db.close()
				repo.shutDown()
