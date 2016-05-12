package se.lu.nateko.cp.meta.test

import org.semanticweb.owlapi.apibinding.OWLManager
import se.lu.nateko.cp.meta.utils.owlapi._
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import se.lu.nateko.cp.meta.utils.sesame.Loading
import org.semanticweb.owlapi.model.PrefixManager
import org.semanticweb.owlapi.util.DefaultPrefixManager
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLDataProperty
import org.semanticweb.owlapi.model.OWLObjectProperty
import org.openrdf.rio.RDFFormat

object TestConfig {
	val manager = OWLManager.createOWLOntologyManager
	val factory = manager.getOWLDataFactory
	lazy val owlOnto = {
		getOntologyFromJarResourceFile("/../classes/owl/uiannotations.owl", manager)
		getOntologyFromJarResourceFile("/../classes/owl/cpmeta.owl", manager)
		getOntologyFromJarResourceFile("/../classes/owl/cpmetaui.owl", manager)
	}

	val instOntUri = "http://meta.icos-cp.eu/resources/cpmeta/"
	val ontUri = "http://meta.icos-cp.eu/ontologies/cpmeta/"

	lazy val instServer: InstanceServer = {
		val repo = Loading.fromResource("/../classes/owl/cpmetainstances.owl", instOntUri)
		val factory = repo.getValueFactory
		val instOnt = factory.createURI(instOntUri)
		val ont = factory.createURI(ontUri)
		Loading.loadResource(repo, "/../classes/owl/cpmeta.owl", ontUri, RDFFormat.RDFXML)
		new SesameInstanceServer(repo, Seq(ont, instOnt), Seq(instOnt))
	}

	private val prefixManager: PrefixManager =
		new DefaultPrefixManager(null, null, ontUri)

	def getOWLClass(localName: String): OWLClass =
		factory.getOWLClass(localName, prefixManager)

	def getDataProperty(localName: String): OWLDataProperty =
		factory.getOWLDataProperty(localName, prefixManager)

	def getObjectProperty(localName: String): OWLObjectProperty =
		factory.getOWLObjectProperty(localName, prefixManager)
}