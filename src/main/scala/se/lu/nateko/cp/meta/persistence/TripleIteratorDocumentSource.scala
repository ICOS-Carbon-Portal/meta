package se.lu.nateko.cp.meta.persistence

import scala.jdk.CollectionConverters.IteratorHasAsJava

import org.eclipse.rdf4j.model.Statement;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.rio.RioMemoryTripleSource;

class TripleIteratorDocumentSource(documentIri: IRI, statements: Iterator[Statement]) extends RioMemoryTripleSource(statements.asJava) {
	override def getDocumentIRI: IRI = documentIri
}
