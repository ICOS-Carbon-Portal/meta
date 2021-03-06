package se.lu.nateko.cp.meta.ingestion

import java.nio.file.{Files, Paths}

import org.eclipse.rdf4j.repository.Repository

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.io.Source
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.services.Rdf4jSparqlRunner

class LocalSparqlConstructExtractor(queryRes: String)(implicit ctxt: ExecutionContext) extends Extractor{

	override def getStatements(repo: Repository): Ingestion.Statements = Future{

		val src = Source.fromInputStream(getClass.getResourceAsStream(queryRes), "UTF-8")
		val queryStr = try{src.mkString} finally{src.close()}

		val query = SparqlQuery(queryStr)
		new Rdf4jSparqlRunner(repo).evaluateGraphQuery(query)
	}

}
