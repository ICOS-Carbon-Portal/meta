package se.lu.nateko.cp.meta.instanceserver

import org.eclipse.rdf4j.model.*
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS
import org.eclipse.rdf4j.model.vocabulary.XSD
import se.lu.nateko.cp.meta.api.CloseableIterator
import se.lu.nateko.cp.meta.api.CloseableIterator.empty
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.UriResource
import se.lu.nateko.cp.meta.utils.Validated
import se.lu.nateko.cp.meta.utils.parseInstant
import se.lu.nateko.cp.meta.utils.rdf4j.===
import se.lu.nateko.cp.meta.utils.rdf4j.toJava

import java.net.{URI => JavaUri}
import java.time.Instant
import java.time.LocalDate
import scala.util.Try

trait InstanceServer extends AutoCloseable{
	import InstanceServer.*
	import CardinalityExpectation.Default

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
	def getConnection(): TriplestoreConnection
	def access[T](read: TriplestoreConnection ?=> T): T =
		given conn: TriplestoreConnection = getConnection()
		try read finally conn.close()

	def shutDown(): Unit

	final def writeContextsView: InstanceServer = withContexts(writeContexts, writeContexts)

	final def addAll(statements: Seq[Statement]): Try[Unit] = applyAll(statements.map(RdfUpdate(_, true)))()
	final def removeAll(statements: Seq[Statement]): Try[Unit] = applyAll(statements.map(RdfUpdate(_, false)))()

	final override def close(): Unit = shutDown()

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
		hasStatement(Option(subject), Option(predicate), Option(obj))

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
			case lit: Literal if(lit.getDatatype === dType) => lit.stringValue
		}.distinct
		assertCardinality(values.size, exp, s"${dType.getLocalName} value(s) of $pred for $subj")
		values
	}

	final def getStringValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): IndexedSeq[String] =
		getLiteralValues(subj, pred, XSD.STRING, exp)

	final def getIntValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): IndexedSeq[Int] =
		getLiteralValues(subj, pred, XSD.INTEGER, exp).map(_.toInt)

	//TODO Go back to .map(_.toLong) parsing or rework all the cardinality validation approach
	//The fix is to work around a broken long value in the RDF (file size of https://meta.icos-cp.eu/objects/I_HZ0N-B0SOWu_hUiD_MMdlG)
	//(rdflog is ok)
	final def getLongValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): IndexedSeq[Long] =
		getLiteralValues(subj, pred, XSD.LONG, exp).flatMap(_.toLongOption)

	final def getDoubleValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): IndexedSeq[Double] =
		getLiteralValues(subj, pred, XSD.DOUBLE, exp).map(_.toDouble)

	final def getFloatValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): IndexedSeq[Float] =
		getLiteralValues(subj, pred, XSD.FLOAT, exp).map(_.toFloat)

	final def getUriLiteralValues(subj: IRI, pred: IRI, exp: CardinalityExpectation = Default): IndexedSeq[JavaUri] =
		getLiteralValues(subj, pred, XSD.ANYURI, exp).map(new JavaUri(_))
}

object InstanceServer:
	enum CardinalityExpectation(val descr: String):
		case AtMostOne extends CardinalityExpectation("at most one")
		case AtLeastOne extends CardinalityExpectation("at least one")
		case ExactlyOne extends CardinalityExpectation("exactly one")
		case Default extends CardinalityExpectation("any amount")

	export CardinalityExpectation.{AtMostOne, AtLeastOne, ExactlyOne, Default}

	private def assertCardinality(actual: Int, expectation: CardinalityExpectation, errorTip: => String): Unit =
		expectation match
			case Default => ()
			case AtMostOne => assert(actual <= 1, s"Expected at most one $errorTip, but got $actual")
			case AtLeastOne => assert(actual >= 1, s"Expected at least one $errorTip, but got $actual")
			case ExactlyOne => assert(actual == 1, s"Expected exactly one $errorTip, but got $actual")


trait TriplestoreConnection extends SparqlRunner with AutoCloseable:
	def primaryContext: IRI
	def readContexts: Seq[IRI]
	def factory: ValueFactory

	def getStatements(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): CloseableIterator[Statement]
	def hasStatement(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): Boolean
	def withContexts(primary: IRI, read: Seq[IRI]): TriplestoreConnection

	final def withReadContexts(read: Seq[IRI]): TriplestoreConnection = withContexts(primaryContext, read)
	final def primaryContextView: TriplestoreConnection = withContexts(primaryContext, Seq(primaryContext))

	final def hasStatement(subject: IRI, predicate: IRI, obj: Value): Boolean =
		hasStatement(Option(subject), Option(predicate), Option(obj))


object TriplestoreConnection:
	type TSC2[T] = TriplestoreConnection ?=> T
	type TSC2V[T] = TSC2[Validated[T]]

	import InstanceServer.*

	def getStatements(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): TSC2[CloseableIterator[Statement]] =
		conn ?=> conn.getStatements(subject, predicate, obj)

	def getStatements(subject: IRI): TSC2[IndexedSeq[Statement]] = getStatements(Some(subject), None, None).toIndexedSeq

	def hasStatement(subject: Option[IRI], predicate: Option[IRI], obj: Option[Value]): TSC2[Boolean] =
		conn ?=> conn.hasStatement(subject, predicate, obj)

	def hasStatement(subject: IRI, predicate: IRI, obj: Value): TSC2[Boolean] =
		conn ?=> conn.hasStatement(Option(subject), Option(predicate), Option(obj))

	def resourceHasType(res: IRI, tpe: IRI): TSC2[Boolean] = hasStatement(res, RDF.TYPE, tpe)

	def getValues(instUri: IRI, propUri: IRI): TSC2[IndexedSeq[Value]] =
		conn ?=> conn.getStatements(Some(instUri), Some(propUri), None).map(_.getObject).toIndexedSeq

	def getLiteralValues(subj: IRI, pred: IRI, dType: IRI): TSC2[IndexedSeq[String]] = getValues(subj, pred)
		.collect:
			case lit: Literal if(lit.getDatatype === dType) => lit.stringValue
		.distinct

	def getUriValues(subj: IRI, pred: IRI): TSC2[IndexedSeq[IRI]] =
		getValues(subj, pred).collect{case uri: IRI => uri}.distinct

	def getStringValues(subj: IRI, pred: IRI): TSC2[IndexedSeq[String]] =
		getLiteralValues(subj, pred, XSD.STRING)

	def getIntValues(subj: IRI, pred: IRI): TSC2[IndexedSeq[Int]] =
		getLiteralValues(subj, pred, XSD.INTEGER).flatMap(_.toIntOption)

	def getLongValues(subj: IRI, pred: IRI): TSC2[IndexedSeq[Long]] =
		getLiteralValues(subj, pred, XSD.LONG).flatMap(_.toLongOption)

	def getDoubleValues(subj: IRI, pred: IRI): TSC2[IndexedSeq[Double]] =
		getLiteralValues(subj, pred, XSD.DOUBLE).flatMap(_.toDoubleOption)

	def getFloatValues(subj: IRI, pred: IRI): TSC2[IndexedSeq[Float]] =
		getLiteralValues(subj, pred, XSD.FLOAT).flatMap(_.toFloatOption)

	def getUriLiteralValues(subj: IRI, pred: IRI): TSC2[IndexedSeq[JavaUri]] =
		getLiteralValues(subj, pred, XSD.ANYURI).map(new JavaUri(_))


	def validate[T](
		getter: (IRI, IRI) => TSC2[IndexedSeq[T]],
		subj: IRI,
		pred: IRI,
		card: CardinalityExpectation
	): TSC2V[IndexedSeq[T]] = Validated(getter(subj, pred)).flatMap: vals =>

		def error: Validated[IndexedSeq[T]] =
			val err = s"Expected ${card.descr} values of property $pred for resource $subj, but got ${vals.length}"
			new Validated(Some(vals).filterNot(_.isEmpty), Seq(err))

		card match
			case AtMostOne  if vals.length  > 1 =>
				error
			case AtLeastOne if vals.length  < 1 =>
				error
			case ExactlyOne if vals.length != 1 =>
				error
			case Default | AtLeastOne | AtMostOne | ExactlyOne =>
				Validated.ok(vals)
	end validate


	def getSingleUri(subj: IRI, pred: IRI): TSC2V[IRI] =
		validate(getUriValues, subj, pred, ExactlyOne).map(_.head)

	def getOptionalUri(subj: IRI, pred: IRI): TSC2V[Option[IRI]] =
		validate(getUriValues, subj, pred, AtMostOne).map(_.headOption)

	def getLabeledResource(subj: IRI, pred: IRI): TSC2V[UriResource] =
		getSingleUri(subj, pred).flatMap(getLabeledResource)

	def getLabeledResource(uri: IRI): TSC2V[UriResource] = getOptionalString(uri, RDFS.LABEL).map: label =>
		UriResource(uri.toJava, label, getStringValues(uri, RDFS.COMMENT))

	def getOptionalString(subj: IRI, pred: IRI): TSC2V[Option[String]] =
		validate(getStringValues, subj, pred, AtMostOne).map(_.headOption)

	def getSingleString(subj: IRI, pred: IRI): TSC2V[String] =
		validate(getStringValues, subj, pred, ExactlyOne).map(_.head)

	def getSingleInt(subj: IRI, pred: IRI): TSC2V[Int] =
		validate(getIntValues, subj, pred, ExactlyOne).map(_.head)

	def getOptionalInt(subj: IRI, pred: IRI): TSC2V[Option[Int]] =
		validate(getIntValues, subj, pred, AtMostOne).map(_.headOption)

	def getOptionalLong(subj: IRI, pred: IRI): TSC2V[Option[Long]] =
		validate(getLongValues, subj, pred, AtMostOne).map(_.headOption)

	def getOptionalDouble(subj: IRI, pred: IRI): TSC2V[Option[Double]] =
		validate(getDoubleValues, subj, pred, AtMostOne).map(_.headOption)

	def getOptionalFloat(subj: IRI, pred: IRI): TSC2V[Option[Float]] =
		validate(getFloatValues, subj, pred, AtMostOne).map(_.headOption)

	def getOptionalBool(subj: IRI, pred: IRI): TSC2V[Option[Boolean]] =
		validate(getLiteralValues(_, _, XSD.BOOLEAN), subj, pred, AtMostOne)
			.map(_.headOption.map(_.toLowerCase == "true"))

	def getSingleDouble(subj: IRI, pred: IRI): TSC2V[Double] =
		validate(getDoubleValues, subj, pred, ExactlyOne).map(_.head)

	def getOptionalInstant(subj: IRI, pred: IRI): TSC2V[Option[Instant]] =
		validate(getLiteralValues(_, _, XSD.DATETIME), subj, pred, AtMostOne).map(_.headOption.map(parseInstant))

	def getSingleInstant(subj: IRI, pred: IRI): TSC2V[Instant] =
		validate(getLiteralValues(_, _, XSD.DATETIME), subj, pred, ExactlyOne).map(_.map(parseInstant).head)

	def getOptionalLocalDate(subj: IRI, pred: IRI): TSC2V[Option[LocalDate]] =
		validate(getLiteralValues(_, _, XSD.DATE), subj, pred, AtMostOne).map(_.headOption.map(LocalDate.parse))

	def getSingleUriLiteral(subj: IRI, pred: IRI): TSC2V[JavaUri] =
		validate(getUriLiteralValues, subj, pred, ExactlyOne).map(_.head)

	def getOptionalUriLiteral(subj: IRI, pred: IRI): TSC2V[Option[JavaUri]] =
		validate(getUriLiteralValues, subj, pred, AtMostOne).map(_.headOption)

	def getHashsum(dataObjUri: IRI, pred: IRI): TSC2V[Sha256Sum] =
		for
			hashLits <- validate(getLiteralValues(_, _, XSD.BASE64BINARY), dataObjUri, pred, ExactlyOne)
			hash <- Validated.fromTry(Sha256Sum.fromBase64(hashLits.head))
		yield hash

end TriplestoreConnection
