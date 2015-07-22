package se.lu.nateko.cp.meta.persistence

import scala.collection.JavaConverters._

import org.openrdf.model.Statement;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.rio.RioMemoryTripleSource;

class TripleIteratorDocumentSource(documentIri: IRI, statements: Iterator[Statement]) extends RioMemoryTripleSource(statements.asJava) {
	override def getDocumentIRI: IRI = documentIri
}
