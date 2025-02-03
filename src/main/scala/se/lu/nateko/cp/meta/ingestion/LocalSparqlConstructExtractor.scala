package se.lu.nateko.cp.meta.ingestion

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.repository.Repository
import se.lu.nateko.cp.meta.api.{CloseableIterator, SparqlQuery}
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner

import java.nio.file.{Files, Paths}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

class LocalSparqlConstructExtractor(queryRes: String, extras: String*)(using ExecutionContext) extends Extractor{

	override def getStatements(repo: Repository): Ingestion.Statements = Future{

		def getOneQueryStatements(queryRes: String): CloseableIterator[Statement] =
			val src = Source.fromInputStream(getClass.getResourceAsStream(queryRes), "UTF-8")
			val queryStr = try{src.mkString} finally{src.close()}

			val query = SparqlQuery(queryStr)
			new Rdf4jSparqlRunner(repo).evaluateGraphQuery(query)

		def concatIterators(qRes: String, extras: Seq[String]): CloseableIterator[Statement] =
			getOneQueryStatements(qRes) ++ extras.headOption.fold(CloseableIterator.empty){
				headQRes => concatIterators(headQRes, extras.tail)
			}

		concatIterators(queryRes, extras)
	}

}
