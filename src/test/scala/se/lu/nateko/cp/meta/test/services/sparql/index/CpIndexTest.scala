import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.services.sparql.magic.CpIndex
import org.eclipse.rdf4j.sail.Sail
import org.eclipse.rdf4j.sail.SailConnection
import org.eclipse.rdf4j.model.ValueFactory
import java.{util => ju}
import java.io.File
import org.eclipse.rdf4j.common.transaction.IsolationLevel
import akka.event.NoLogging
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.base.CoreDatatype
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.IRI
import javax.xml.datatype.XMLGregorianCalendar
import org.eclipse.rdf4j.model.BNode
import java.math.BigInteger
import org.eclipse.rdf4j.model.Literal
import scala.concurrent.Future
import se.lu.nateko.cp.meta.services.sparql.magic.GeoIndex
import org.eclipse.rdf4j.query.Dataset
import org.eclipse.rdf4j.query.algebra.TupleExpr
import org.eclipse.rdf4j.sail.UpdateContext
import org.eclipse.rdf4j.query.BindingSet
import org.eclipse.rdf4j.model.Namespace
import org.eclipse.rdf4j.common.iteration.CloseableIteration

class CpIndexTest extends AnyFunSpec {

	class DummyIRI extends IRI {
		override def getLocalName(): String = ???
		override def getNamespace(): String = ???
		override def stringValue(): String = ???
	}

	class DummyValueFactory extends ValueFactory {
		override def createStatement(subject: Resource, predicate: IRI, obj: Value): Statement = ???
		override def createStatement(subject: Resource, predicate: IRI, obj: Value, context: Resource): Statement = ???
		override def createLiteral(label: String): Literal = ???
		override def createLiteral(label: String, language: String): Literal = ???
		override def createLiteral(label: String, datatype: IRI): Literal = ???
		override def createLiteral(label: String, datatype: CoreDatatype): Literal = ???
		override def createLiteral(label: String, datatype: IRI, coreDatatype: CoreDatatype): Literal = ???
		override def createLiteral(value: Boolean): Literal = ???
		override def createLiteral(value: Byte): Literal = ???
		override def createLiteral(value: Short): Literal = ???
		override def createLiteral(value: Int): Literal = ???
		override def createLiteral(value: Long): Literal = ???
		override def createLiteral(value: Float): Literal = ???
		override def createLiteral(value: Double): Literal = ???
		override def createLiteral(bigDecimal: java.math.BigDecimal): Literal = ???
		override def createLiteral(bigInteger: BigInteger): Literal = ???
		override def createLiteral(calendar: XMLGregorianCalendar): Literal = ???
		override def createLiteral(date: ju.Date): Literal = ???
		override def createBNode(): BNode = ???
		override def createBNode(nodeID: String): BNode = ???
		override def createIRI(iri: String): IRI = DummyIRI()
		override def createIRI(namespace: String, localName: String): IRI = DummyIRI()
	}

	class DummyConnection extends SailConnection {
		override def setNamespace(prefix: String, name: String): Unit = ???
		override def close(): Unit = {}
		override def getContextIDs(): CloseableIteration[? <: Resource] = ???
		override def getStatements(subj: Resource, pred: IRI, obj: Value, includeInferred: Boolean, contexts: Resource*): CloseableIteration[? <: Statement] = ???
		override def getNamespaces(): CloseableIteration[? <: Namespace] = ???
		override def removeNamespace(prefix: String): Unit = ???
		override def startUpdate(op: UpdateContext): Unit = ???
		override def isOpen(): Boolean = ???
		override def removeStatement(op: UpdateContext, subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit = ???
		override def endUpdate(op: UpdateContext): Unit = ???
		override def addStatement(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit = ???
		override def addStatement(op: UpdateContext, subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit = ???
		override def clear(contexts: Resource*): Unit = ???
		override def commit(): Unit = ???
		override def evaluate(tupleExpr: TupleExpr, dataset: Dataset, bindings: BindingSet, includeInferred: Boolean): CloseableIteration[? <: BindingSet] = ???
		override def removeStatements(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit = ???
		override def size(contexts: Resource*): Long = ???
		override def flush(): Unit = ???
		override def begin(): Unit = ???
		override def begin(level: IsolationLevel): Unit = ???
		override def clearNamespaces(): Unit = ???
		override def isActive(): Boolean = ???
		override def prepare(): Unit = ???
		override def getNamespace(prefix: String): String = ???
		override def rollback(): Unit = ???
	}
	
	class DummySail extends  Sail {
		val valFactory = DummyValueFactory()
		override def isWritable(): Boolean = ???
		override def getConnection(): SailConnection = DummyConnection()
		override def shutDown(): Unit = ???
		override def init(): Unit = ???
		override def getSupportedIsolationLevels(): ju.List[IsolationLevel] = ???
		override def getDefaultIsolationLevel(): IsolationLevel = ???
		override def setDataDir(dataDir: File): Unit = ???
		override def getValueFactory(): ValueFactory = valFactory
		override def getDataDir(): File = ???
	}

	describe("processUpdate"){
		it("clears fName of ObjEntry when hasName tuple is deleted"){
			val sail = DummySail();
			val index = CpIndex(sail, Future.never, 100)(NoLogging);
		}
	}
}
