package se.lu.nateko.cp.meta

import org.semanticweb.owlapi.model.OWLDataFactory
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.util.DefaultPrefixManager
import org.semanticweb.owlapi.model.PrefixManager
import org.semanticweb.owlapi.model.OWLDataProperty
import org.semanticweb.owlapi.model.OWLAnnotationProperty
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLObjectProperty

object Vocab {

	val ontoIri: IRI = IRI.create("http://meta.icos-cp.eu/ontologies/cpmeta/")

	private val factory: OWLDataFactory =
		OWLManager.createOWLOntologyManager.getOWLDataFactory

	private val prefixManager: PrefixManager =
		new DefaultPrefixManager(null, null, ontoIri.toString)

	def getOWLClass(localName: String): OWLClass =
		factory.getOWLClass(localName, prefixManager)

	def getDataProperty(localName: String): OWLDataProperty =
		factory.getOWLDataProperty(localName, prefixManager)

	def getObjectProperty(localName: String): OWLObjectProperty =
		factory.getOWLObjectProperty(localName, prefixManager)
		
	def getAnnotationProperty(localName: String): OWLAnnotationProperty =
		factory.getOWLAnnotationProperty(localName, prefixManager)

	val exposedToUsersAnno: OWLAnnotationProperty = getAnnotationProperty("isExposedToUsers")
	val displayPropAnno: OWLAnnotationProperty = getAnnotationProperty("displayProperty")
	val displayPropAnnos: IndexedSeq[OWLAnnotationProperty] =
		(1 to 4).map(i => getAnnotationProperty(s"displayProperty$i"))
}