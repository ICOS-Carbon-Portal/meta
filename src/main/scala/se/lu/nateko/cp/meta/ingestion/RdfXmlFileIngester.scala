package se.lu.nateko.cp.meta.ingestion

import org.eclipse.rdf4j.rio.RDFFormat
import se.lu.nateko.cp.meta.utils.rdf4j.*
import org.eclipse.rdf4j.model.ValueFactory
import scala.concurrent.Future
import scala.concurrent.ExecutionContext


//TODO Consider rewriting using a parser only, without loading all statements into memory
class RdfFileIngester(resourcePath: String, format: RDFFormat)(using ExecutionContext) extends Ingester{

	private val baseUri = "http://dummy.org"

	def getStatements(valueFactory: ValueFactory): Ingestion.Statements = Future{

		val repo = Loading.fromResource(resourcePath, baseUri, format)

		repo.access(
			_.getStatements(null, null, null, false, valueFactory.createIRI(baseUri)),
			() => repo.shutDown()
		)

	}
}

class RdfXmlFileIngester(resourcePath: String)(using ExecutionContext) extends RdfFileIngester(resourcePath, RDFFormat.RDFXML)
