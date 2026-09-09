package se.lu.nateko.cp.meta.services.upload

import scala.language.unsafeNulls
import java.net.URI

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.{IRI, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.eclipse.rdf4j.model.vocabulary.{RDF, RDFS}
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.repository.sail.SailRepository
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.{CloseableIterator, RdfLens, SparqlRunner}
import se.lu.nateko.cp.meta.instanceserver.{RdfStatement, TriplestoreConnection}
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, EnvriConfigs}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab, Rdf4jSparqlRunner}
import se.lu.nateko.cp.meta.utils.rdf4j.*

class DatasetVariableBatchTests extends AnyFunSpec:

	private val factory = SimpleValueFactory.getInstance()
	private val metaVocab = CpmetaVocab(factory)
	private val reader = new CpmetaReader:
		override val metaVocab: CpmetaVocab = DatasetVariableBatchTests.this.metaVocab

	describe("batched dataset variable metadata"):

		it("loads variables, columns, value types and quantity kinds with one graph query"):
			val graph = iri("graph")
			val dataset = iri("dataset")
			val variable = iri("variable")
			val column = iri("column")
			val valueType = iri("valueType")
			val quantityKind = iri("quantityKind")
			val flaggedColumn = iri("flaggedColumn")
			val valueFormat = iri("valueFormat")

			val repo = SailRepository(MemoryStore())
			repo.init()
			val repoConn = repo.getConnection()
			try
				repoConn.add(dataset, metaVocab.hasVariable, variable, graph)
				repoConn.add(dataset, metaVocab.hasColumn, column, graph)
				repoConn.add(variable, RDFS.LABEL, factory.createLiteral("Air temperature"), graph)
				repoConn.add(variable, RDFS.COMMENT, factory.createLiteral("Measured variable"), graph)
				repoConn.add(variable, metaVocab.hasVariableTitle, factory.createLiteral("TA"), graph)
				repoConn.add(variable, metaVocab.hasValueType, valueType, graph)
				repoConn.add(variable, metaVocab.hasValueFormat, valueFormat, graph)
				repoConn.add(variable, metaVocab.isQualityFlagFor, flaggedColumn, graph)
				repoConn.add(column, RDFS.LABEL, factory.createLiteral("Flux column"), graph)
				repoConn.add(column, metaVocab.hasColumnTitle, factory.createLiteral("FC_.*"), graph)
				repoConn.add(column, metaVocab.hasValueType, valueType, graph)
				repoConn.add(column, metaVocab.isRegexColumn, factory.createLiteral(true), graph)
				repoConn.add(column, metaVocab.isOptionalColumn, factory.createLiteral(true), graph)
				repoConn.add(valueType, RDFS.LABEL, factory.createLiteral("Temperature"), graph)
				repoConn.add(valueType, RDFS.COMMENT, factory.createLiteral("A temperature value"), graph)
				repoConn.add(valueType, metaVocab.hasQuantityKind, quantityKind, graph)
				repoConn.add(valueType, metaVocab.hasUnit, factory.createLiteral("degC"), graph)
				repoConn.add(quantityKind, RDFS.LABEL, factory.createLiteral("Temperature"), graph)
				repoConn.add(quantityKind, RDFS.COMMENT, factory.createLiteral("Thermodynamic temperature"), graph)

				val sparql = CountingSparqlRunner(Rdf4jSparqlRunner(repo))
				val conn = QueryOnlyConnection(graph, factory)
				val metaConn = RdfLens.metaLens(graph.toJava, Seq(graph.toJava))(using conn)
				val result = reader.getValTypeLookupBatched(dataset)(using metaConn, sparql)

				assert(result.errors.isEmpty)
				assert(sparql.graphQueries.size === 1)
				assert(sparql.graphQueries.head.contains(s"FROM <${graph.stringValue}>"))

				val lookup = result.result.get
				val variableMeta = lookup.lookup("TA").get
				assert(variableMeta.model.label.contains("Air temperature"))
				assert(variableMeta.valueType.self.label.contains("Temperature"))
				assert(variableMeta.valueType.quantityKind.flatMap(_.label).contains("Temperature"))
				assert(variableMeta.valueType.unit.contains("degC"))
				assert(variableMeta.valueFormat.contains(valueFormat.toJava))
				assert(variableMeta.isFlagFor.contains(Seq(flaggedColumn.toJava)))
				assert(lookup.lookup("FC_1").exists(_.label == "FC_1"))
				assert(lookup.plainMandatory.map(_.label) === Seq("TA"))
			finally
				repoConn.close()
				repo.shutDown()

		it("selects deployment/instrument pairs from the read graph in one query"):
			given EnvriConfigs = Map(Envri.ICOS -> EnvriConfig(
				authHost = "example.org", dataHost = "example.org", metaHost = "example.org",
				dataItemPrefix = URI("https://example.org/"), metaItemPrefix = URI("https://example.org/"), defaultTimezoneId = "UTC"
			))
			val graph = iri("graph")
			val otherGraph = iri("otherGraph")
			val station = iri("station")
			val firstDeployment = iri("firstDeployment")
			val secondDeployment = iri("secondDeployment")
			val instrument = iri("instrument")
			val reader = new DobjMetaReader(CpVocab(factory)):
				override val metaVocab: CpmetaVocab = DatasetVariableBatchTests.this.metaVocab
			val repo = SailRepository(MemoryStore())
			repo.init()
			val repoConn = repo.getConnection()
			try
				repoConn.add(firstDeployment, RDF.TYPE, metaVocab.ssn.deploymentClass, graph)
				repoConn.add(firstDeployment, metaVocab.atOrganization, station, graph)
				repoConn.add(instrument, metaVocab.ssn.hasDeployment, firstDeployment, graph)
				repoConn.add(instrument, RDFS.LABEL, factory.createLiteral("Instrument label"), graph)
				repoConn.add(secondDeployment, RDF.TYPE, metaVocab.ssn.deploymentClass, graph)
				repoConn.add(secondDeployment, metaVocab.atOrganization, station, graph)
				repoConn.add(iri("otherDeployment"), RDF.TYPE, metaVocab.ssn.deploymentClass, otherGraph)
				repoConn.add(iri("otherDeployment"), metaVocab.atOrganization, station, otherGraph)

				val rows = Rdf4jSparqlRunner(repo).evaluateTupleQuery(reader.deploymentInstrumentsQuery(station, Seq(graph))).toIndexedSeq
				assert(rows.map(_.getValue("deployment")).toSet === Set(firstDeployment, secondDeployment))
				assert(rows.find(_.getValue("deployment") == firstDeployment).flatMap(row => Option(row.getValue("instrument"))).contains(instrument))
				assert(rows.find(_.getValue("deployment") == secondDeployment).flatMap(row => Option(row.getValue("instrument"))).isEmpty)

				val prefetched = Rdf4jSparqlRunner(repo)
					.evaluateGraphQuery(reader.prefetchedStatementsQuery(Seq(firstDeployment, instrument), Seq(graph)))
					.toIndexedSeq
				assert(prefetched.exists(statement => statement.getSubject == instrument && statement.getPredicate == RDFS.LABEL))
				assert(!prefetched.exists(_.getSubject == iri("otherDeployment")))
			finally
				repoConn.close()
				repo.shutDown()

	private def iri(localName: String): IRI = factory.createIRI("https://example.org/", localName)

	private final case class CountingSparqlRunner(delegate: SparqlRunner) extends SparqlRunner:
		val graphQueries = scala.collection.mutable.ArrayBuffer.empty[String]

		override def evaluateGraphQuery(query: String): CloseableIterator[Statement] =
			graphQueries += query
			delegate.evaluateGraphQuery(query)

		override def evaluateTupleQuery(query: String): CloseableIterator[BindingSet] =
			delegate.evaluateTupleQuery(query)

	private final case class QueryOnlyConnection(primaryContext: IRI, factory: ValueFactory) extends TriplestoreConnection:
		override val readContexts: Seq[IRI] = Seq(primaryContext)

		override def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[RdfStatement] =
			throw AssertionError("The batched parser must not read from the remote connection")

		override def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean =
			throw AssertionError("The batched parser must not read from the remote connection")

		override def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection = this
		override def close(): Unit = ()
