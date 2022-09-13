package se.lu.nateko.cp.meta.ingestion

import java.nio.file.{Files, Paths}

import org.eclipse.rdf4j.repository.Repository

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.io.Source
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner
import se.lu.nateko.cp.meta.api.CloseableIterator
import org.eclipse.rdf4j.model.Statement

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
