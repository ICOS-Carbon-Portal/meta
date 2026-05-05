package se.lu.nateko.cp.meta.onto

import scala.language.unsafeNulls

import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.{IRI, OWLAnnotationProperty, OWLDataFactory, PrefixManager}
import org.semanticweb.owlapi.util.DefaultPrefixManager

object Vocab {

	val ontoIri: IRI = IRI.create("http://meta.icos-cp.eu/ontologies/uiannotations/")

	private val factory: OWLDataFactory =
		OWLManager.createOWLOntologyManager.getOWLDataFactory

	private val prefixManager: PrefixManager =
		new DefaultPrefixManager(null, null, ontoIri.toString)

	def getAnnotationProperty(localName: String): OWLAnnotationProperty =
		factory.getOWLAnnotationProperty(localName, prefixManager)

	val exposedToUsersAnno: OWLAnnotationProperty = getAnnotationProperty("isExposedToUsers")
	val newInstanceBaseUriAnno: OWLAnnotationProperty = getAnnotationProperty("newInstanceBaseUri")
	val displayPropAnno: OWLAnnotationProperty = getAnnotationProperty("displayProperty")
	val displayPropAnnos: IndexedSeq[OWLAnnotationProperty] =
		(1 to 5).map(i => getAnnotationProperty(s"displayProperty$i"))
}
