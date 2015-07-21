package se.lu.nateko.cp.meta.persistence.test

import org.scalatest.FunSpec
import org.semanticweb.owlapi.apibinding.OWLManager
import org.openrdf.model.impl.ValueFactoryImpl
import org.openrdf.model.vocabulary.OWL
import org.openrdf.model.vocabulary.RDF
import org.openrdf.model.Statement
import se.lu.nateko.cp.meta.persistence.TripleIteratorDocumentSource
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.rio.RioMemoryTripleSource
import org.semanticweb.owlapi.rio.RioNTriplesParserFactory
import scala.collection.JavaConverters._
import org.semanticweb.owlapi.io.OWLParserFactory
import se.lu.nateko.cp.meta.Onto

class TripleIteratorDocumentSourceTest extends FunSpec{

	private def getIter[T](elems: T*): java.util.Iterator[T] = {
		val arr = new java.util.ArrayList[T]
		elems.foreach(arr.add)
		arr.iterator
	}

	describe("TripleIteratorDocumentSource"){

		val ontIri = IRI.create("http://www.icos-cp.eu/ontology/")

		val manager = OWLManager.createOWLOntologyManager
		manager.setOntologyParsers(Set[OWLParserFactory](new RioNTriplesParserFactory).asJava)

		it("Can be used as OWLOntologyDocumentSource to load an ontology"){
			val f = new ValueFactoryImpl()
			val person = f.createURI("http://www.icos-cp.eu/ontology/Person")
			val statement = f.createStatement(person, RDF.TYPE, OWL.CLASS)

			val source = new TripleIteratorDocumentSource(ontIri, getIter(statement))
			val owlOnt = manager.loadOntologyFromOntologyDocument(source)
			val onto = new Onto(owlOnt)

			val person2 = onto.getTopLevelClasses.head.uri.toString
			assert(person.toString === person2)
		}

	}

}