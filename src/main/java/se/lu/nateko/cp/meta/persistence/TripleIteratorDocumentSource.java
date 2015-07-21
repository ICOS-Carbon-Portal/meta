package se.lu.nateko.cp.meta.persistence;

import java.util.Iterator;

import org.openrdf.model.Statement;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.rio.RioMemoryTripleSource;

public class TripleIteratorDocumentSource extends RioMemoryTripleSource {

	private final IRI documentIRI;

	public TripleIteratorDocumentSource(IRI documentIri, Iterator<Statement> statements) {
		super(statements);
		this.documentIRI = documentIri;
	}

	@Override
	public IRI getDocumentIRI() {
		return documentIRI;
	}
}
