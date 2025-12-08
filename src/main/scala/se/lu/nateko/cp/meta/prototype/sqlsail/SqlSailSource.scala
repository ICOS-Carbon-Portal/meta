package se.lu.nateko.cp.meta.prototype.ntriples

import scala.language.unsafeNulls

import org.eclipse.rdf4j.common.transaction.IsolationLevel
import org.eclipse.rdf4j.sail.base.{BackingSailSource, SailDataset, SailSink}
import org.eclipse.rdf4j.common.iteration.CloseableIteration
import org.eclipse.rdf4j.model.{IRI, Namespace, Resource, Statement, Value}
import org.eclipse.rdf4j.common.iteration.CloseableIteratorIteration
import scala.jdk.CollectionConverters.IteratorHasAsJava
import java.sql.Connection
import org.eclipse.rdf4j.sail.base.SailStore
import org.eclipse.rdf4j.sail.base.SailSource
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.query.algebra.evaluation.impl.EvaluationStatistics
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.slf4j.LoggerFactory
import se.lu.nateko.cp.meta.persistence.postgres.{Postgres, DbServer, DbCredentials}
import scala.collection.mutable

class SqlSailSource(store: Store, explicit: Boolean) extends BackingSailSource {
	override def dataset(level: IsolationLevel): SailDataset = new Dataset(store, explicit)
	override def sink(level: IsolationLevel): SailSink = new NoopSink()
}

class Dataset(store: Store, explicit: Boolean) extends SailDataset {
	private val logger = LoggerFactory.getLogger(getClass)

	override def getStatements(
		subj: Resource,
		pred: IRI,
		obj: Value,
		contexts: Resource*
	): CloseableIteration[Statement] = {
		logger.info(s"Get statement: $subj $pred $obj")

		val conn = store.getConnection()
		try {
			val (sql, params) = buildQuery(subj, pred, obj)
			val stmt = conn.prepareStatement(sql)

			// Set parameters
			params.zipWithIndex.foreach { case (param, idx) =>
				stmt.setString(idx + 1, param)
			}

			val rs = stmt.executeQuery()
			val statements = mutable.ArrayBuffer[Statement]()

			try {
				while (rs.next()) {
					val subjectStr = rs.getString("subject")
					val predicateStr = rs.getString("predicate")
					val objectStr = rs.getString("object")

					val s = store.getValueFactory.createIRI(subjectStr)
					val p = store.getValueFactory.createIRI(predicateStr)
					val o = parseObject(objectStr)

					statements += store.getValueFactory.createStatement(s, p, o)
				}
			} finally {
				rs.close()
				stmt.close()
			}

			new CloseableIteratorIteration(statements.iterator.asJava)
		} finally {
			conn.close()
		}
	}

	private def buildQuery(
		subj: Resource,
		pred: IRI,
		obj: Value
	): (String, Seq[String]) = {
		val conditions = mutable.ArrayBuffer[String]()
		val params = mutable.ArrayBuffer[String]()

		var sql = s"SELECT subject, predicate, object FROM ${store.getTableName}"

		if (subj != null) {
			conditions += "subject = ?"
			params += subj.stringValue()
		}
		if (pred != null) {
			conditions += "predicate = ?"
			params += pred.stringValue()
		}
		if (obj != null) {
			conditions += "object = ?"
			params += obj.stringValue()
		}

		if (conditions.nonEmpty) {
			sql += " WHERE " + conditions.mkString(" AND ")
		}

		(sql, params.toSeq)
	}

	private def parseObject(objectStr: String): Value = {
		// Simple heuristic: if starts with http:// or https://, treat as IRI
		// Otherwise treat as literal
		if (objectStr.startsWith("http://") || objectStr.startsWith("https://")) {
			store.getValueFactory.createIRI(objectStr)
		} else {
			// Check for language tag or datatype
			// Format: "value"@lang or "value"^^datatype
			if (objectStr.contains("@") && !objectStr.contains("^^")) {
				val atIndex = objectStr.lastIndexOf("@")
				val value = objectStr.substring(0, atIndex).stripPrefix("\"").stripSuffix("\"")
				val lang = objectStr.substring(atIndex + 1)
				store.getValueFactory.createLiteral(value, lang)
			} else if (objectStr.contains("^^")) {
				val parts = objectStr.split("\\^\\^", 2)
				val value = parts(0).stripPrefix("\"").stripSuffix("\"")
				val datatype = store.getValueFactory.createIRI(parts(1))
				store.getValueFactory.createLiteral(value, datatype)
			} else {
				// Plain literal
				val value = objectStr.stripPrefix("\"").stripSuffix("\"")
				store.getValueFactory.createLiteral(value)
			}
		}
	}

	override def getContextIDs(): CloseableIteration[Resource] = {
		return new CloseableIteratorIteration(Set().iterator.asJava)
	}

	override def getNamespaces(): CloseableIteration[Namespace] = {
		return new CloseableIteratorIteration(Set().iterator.asJava)
	}

	override def getNamespace(prefix: String): String = {
		""
	}

	override def close(): Unit = {
		// No resources to clean up
	}
}

class NoopSink() extends SailSink {
	override def approve(subj: Resource, pred: IRI, obj: Value, context: Resource): Unit = {}
	override def deprecate(st: Statement): Unit = {}
	override def clear(contexts: Resource*): Unit = {}
	override def setNamespace(prefix: String, name: String): Unit = {}
	override def removeNamespace(prefix: String): Unit = {}
	override def clearNamespaces(): Unit = {}
	override def prepare(): Unit = {}
	override def flush(): Unit = {}
	override def close(): Unit = {}
	override def observe(subj: Resource, pred: IRI, obj: Value, contexts: Resource*): Unit = {}
}

class Store(server: DbServer, credentials: DbCredentials, tableName: String = "triples") extends SailStore {

	private val valueFactory = SimpleValueFactory.getInstance()
	private val explicitSource = new SqlSailSource(this, explicit = true)
	private val inferredSource = new SqlSailSource(this, explicit = false)

	def getConnection(): Connection = {
		Postgres.getConnection(server, credentials).get
	}

	def getServer: DbServer = server
	def getCredentials: DbCredentials = credentials
	def getTableName: String = tableName

	override def getValueFactory: ValueFactory = valueFactory
	override def getExplicitSailSource: SailSource = explicitSource
	override def getInferredSailSource: SailSource = inferredSource
	override def getEvaluationStatistics: EvaluationStatistics = new EvaluationStatistics()

	override def close(): Unit = {}
}
