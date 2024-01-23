package se.lu.nateko.cp.meta.services.sparql.magic

import org.eclipse.rdf4j.sail.Sail
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import org.eclipse.rdf4j.sail.SailConnection
import scala.util.Using
import se.lu.nateko.cp.meta.services.CpmetaVocab
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import se.lu.nateko.cp.meta.api.CloseableIterator
import org.eclipse.rdf4j.model.Statement
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jIterationIterator
import se.lu.nateko.cp.meta.utils.rdf4j.Rdf4jStatement
import org.eclipse.rdf4j.model.vocabulary.RDF

class GeoIndexProvider(using ExecutionContext):

	def apply(sail: Sail, cpIndex: CpIndex, metaVocab: CpmetaVocab) = Future(
		Future.fromTry:
			Using(sail.getConnection)(index(_, cpIndex, metaVocab))
	).flatten

	private def index(conn: SailConnection, cpIndex: CpIndex, metaVocab: CpmetaVocab): GeoIndex =

		def getStatements(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): CloseableIterator[Statement] =
			Rdf4jIterationIterator(conn.getStatements(subject, predicate, obj, false))

		def hasStatement(subject: IRI | Null, predicate: IRI | Null, obj: Value | Null): Boolean =
			conn.hasStatement(subject, predicate, obj, false)

		val geo = new GeoIndex

		val itemsWithCoverage: Iterator[(IRI, IRI)] = getStatements(null, metaVocab.hasSpatialCoverage, null).collect:
			case Rdf4jStatement(item, _, cov: IRI) => item -> cov
			//if hasStatement(item, RDF.TYPE, metaVocab.dataObjectClass)
		???
	private def getGeoEvent(item: IRI, coverage: IRI)(using conn: SailConnection): Option[GeoEvent] = ???

end GeoIndexProvider

