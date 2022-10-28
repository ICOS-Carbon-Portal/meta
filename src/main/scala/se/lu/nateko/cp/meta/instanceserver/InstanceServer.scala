package se.lu.nateko.cp.meta.instanceserver

import java.net.{URI => JavaUri}
import org.eclipse.rdf4j.model.*
import org.eclipse.rdf4j.model.vocabulary.{RDF, XSD}
import scala.util.Try
import se.lu.nateko.cp.meta.api.CloseableIterator

trait InstanceServer extends AutoCloseable{
	import InstanceServer.*

	/**
	 * Makes a new IRI for the new instance, but does not add any triples to the repository.
	 * @param prefix The prefix to start the new IRI with
	 */
	def makeNewInstance(prefix: IRI): IRI
	def readContexts: Seq[IRI]
	def writeContexts: Seq[IRI]
	def factory: ValueFactory

	def getStatements(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): CloseableIterator[Statement]
	def hasStatement(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): Boolean
	def filterNotContainedStatements(statements: IterableOnce[Statement]): Seq[Statement]
	def applyAll(updates: Seq[RdfUpdate])(cotransact: => Unit = ()): Try[Unit]
	def withContexts(read: Seq[IRI], write: Seq[IRI]): InstanceServer
	final def writeContextsView: InstanceServer = withContexts(writeContexts, writeContexts)

	final override def close(): Unit = shutDown()
	def shutDown(): Unit = {}

	final def addAll(statements: Seq[Statement]): Try[Unit] = applyAll(statements.map(RdfUpdate(_, true)))()
	final def removeAll(statements: Seq[Statement]): Try[Unit] = applyAll(statements.map(RdfUpdate(_, false)))()

	final def getInstances(classUri: IRI): IndexedSeq[IRI] =
		getStatements(None, Some(RDF.TYPE), Some(classUri))
			.map(_.getSubject)
			.collect{case uri: IRI => uri}
			.toIndexedSeq

	final def getStatements(instUri: IRI): IndexedSeq[Statement] = getStatements(Some(instUri), None, None).toIndexedSeq

	final def getValues(instUri: IRI, propUri: IRI): IndexedSeq[Value] =
		getStatements(Some(instUri), Some(propUri), None)
			.map(_.getObject)
			.toIndexedSeq

	final def hasStatement(subject: IRI, predicate: IRI, obj: Value): Boolean =
		hasStatement(Some(subject), Some(predicate), Some(obj))

	final def resourceHasType(res: IRI, tpe: IRI): Boolean = hasStatement(res, RDF.TYPE, tpe)

	final def add(statements: Statement*): Try[Unit] = addAll(statements)
	final def remove(statements: Statement*): Try[Unit] = removeAll(statements)

	final def addInstance(instUri: IRI, classUri: IRI): Try[Unit] =
		add(factory.createStatement(instUri, RDF.TYPE, classUri))

	final def removeInstance(instUri: IRI): Unit = removeAll(getStatements(instUri))

	final def addPropertyValue(instUri: IRI, propUri: IRI, value: Value): Try[Unit] =
		add(factory.createStatement(instUri, propUri, value))

	final def removePropertyValue(instUri: IRI, propUri: IRI, value: Value): Try[Unit] =
		remove(factory.createStatement(instUri, propUri, value))

	final def applyDiff(from: Seq[Statement], to: Seq[Statement]): Unit = {
		val toRemove = from.diff(to)
		val toAdd = to.diff(from)

		applyAll(toRemove.map(RdfUpdate(_, false)) ++ toAdd.map(RdfUpdate(_, true)))()
	}

	final def getUriValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): IndexedSeq[IRI] = {
		val values = getValues(subj, pred).collect{case uri: IRI => uri}.distinct
		assertCardinality(values.size, exp, s"IRI value(s) of $pred for $subj")
		values
	}

	final def getTypes(res: IRI): IndexedSeq[IRI] = getValues(res, RDF.TYPE).collect{
		case classUri: IRI => classUri
	}

	final def getLiteralValues(subj: IRI, pred: IRI, dType: IRI, exp: CardinalityExpectation = Default): IndexedSeq[String] = {
		val values = getValues(subj, pred).collect{
			case lit: Literal if(lit.getDatatype == dType) => lit.stringValue
		}.distinct
		assertCardinality(values.size, exp, s"${dType.getLocalName} value(s) of $pred for $subj")
		values
	}

	final def getStringValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): IndexedSeq[String] =
		getLiteralValues(subj, pred, XSD.STRING, exp)

	final def getIntValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): IndexedSeq[Int] =
		getLiteralValues(subj, pred, XSD.INTEGER, exp).map(_.toInt)

	final def getLongValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): IndexedSeq[Long] =
		getLiteralValues(subj, pred, XSD.LONG, exp).map(_.toLong)

	final def getDoubleValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): IndexedSeq[Double] =
		getLiteralValues(subj, pred, XSD.DOUBLE, exp).map(_.toDouble)

	final def getFloatValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): IndexedSeq[Float] =
		getLiteralValues(subj, pred, XSD.FLOAT, exp).map(_.toFloat)

	final def getUriLiteralValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): IndexedSeq[JavaUri] =
		getLiteralValues(subj, pred, XSD.ANYURI, exp).map(new JavaUri(_))
}

object InstanceServer{
	sealed trait CardinalityExpectation
	case object AtMostOne extends CardinalityExpectation
	case object AtLeastOne extends CardinalityExpectation
	case object ExactlyOne extends CardinalityExpectation
	case object Default extends CardinalityExpectation

	private def assertCardinality(actual: Int, expectation: CardinalityExpectation, errorTip: => String): Unit = {
		expectation match{
			case Default => ()
			case AtMostOne => assert(actual <= 1, s"Expected at most one $errorTip, but got $actual")
			case AtLeastOne => assert(actual >= 1, s"Expected at least one $errorTip, but got $actual")
			case ExactlyOne => assert(actual == 1, s"Expected exactly one $errorTip, but got $actual")
		}
	}
}
