package se.lu.nateko.cp.meta.test.persistence

import org.scalatest.FunSpec
import org.semanticweb.owlapi.apibinding.OWLManager
import org.openrdf.model.impl.ValueFactoryImpl
import org.openrdf.model.vocabulary.OWL
import org.openrdf.model.vocabulary.RDF
import se.lu.nateko.cp.meta.persistence.TripleIteratorDocumentSource
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.rio.RioNTriplesParserFactory
import scala.collection.JavaConverters._
import org.semanticweb.owlapi.io.OWLParserFactory
import se.lu.nateko.cp.meta.onto.Onto
import scala.Iterator

class TripleIteratorDocumentSourceTest extends FunSpec{

	describe("TripleIteratorDocumentSource"){

		val ontIri = IRI.create("http://www.icos-cp.eu/ontology/")

		val manager = OWLManager.createOWLOntologyManager
		manager.setOntologyParsers(Set[OWLParserFactory](new RioNTriplesParserFactory).asJava)

		it("Can be used as OWLOntologyDocumentSource to load an ontology"){
			val f = new ValueFactoryImpl()
			val person = f.createURI("http://www.icos-cp.eu/ontology/Person")
			val statement = f.createStatement(person, RDF.TYPE, OWL.CLASS)

			val source = new TripleIteratorDocumentSource(ontIri, Iterator(statement))
			val owlOnt = manager.loadOntologyFromOntologyDocument(source)
			val onto = new Onto(owlOnt)

			val person2 = onto.getTopLevelClasses.head.getIRI.toString
			assert(person.toString === person2)
		}

	}

}