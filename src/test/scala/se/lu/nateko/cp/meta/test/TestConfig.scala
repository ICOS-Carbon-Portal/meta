package se.lu.nateko.cp.meta.test

import eu.icoscp.envri.Envri
import java.net.URI
import org.eclipse.rdf4j.rio.RDFFormat
import org.semanticweb.owlapi.apibinding.OWLManager
import org.semanticweb.owlapi.model.{OWLClass, OWLDataProperty, OWLObjectProperty, OWLOntology, PrefixManager}
import org.semanticweb.owlapi.util.DefaultPrefixManager
import se.lu.nateko.cp.meta.core.data.{EnvriConfig, EnvriConfigs}
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, Rdf4jInstanceServer}
import se.lu.nateko.cp.meta.utils.owlapi.*
import se.lu.nateko.cp.meta.utils.rdf4j.Loading

object TestConfig {
	val manager = OWLManager.createOWLOntologyManager
	val factory = manager.getOWLDataFactory
	lazy val owlOnto: OWLOntology = {
		getOntologyFromJarResourceFile("/../classes/owl/uiannotations.owl", manager)
		getOntologyFromJarResourceFile("/../classes/owl/cpmeta.owl", manager)
		getOntologyFromJarResourceFile("/owl/cpmetaui.owl", manager)
	}

	val instOntUri = "http://meta.icos-cp.eu/resources/cpmeta/"
	val ontUri = "http://meta.icos-cp.eu/ontologies/cpmeta/"

	lazy val instServer: InstanceServer = {
		val repo = Loading.fromResource("/owl/cpmetainstances.owl", instOntUri)
		val factory = repo.getValueFactory
		val instOnt = factory.createIRI(instOntUri)
		val ont = factory.createIRI(ontUri)
		Loading.loadResource(repo, "/../classes/owl/cpmeta.owl", ontUri, RDFFormat.RDFXML)
		new Rdf4jInstanceServer(repo, Seq(ont, instOnt), instOnt)
	}

	private val prefixManager: PrefixManager =
		new DefaultPrefixManager(null, null, ontUri)

	def getOWLClass(localName: String): OWLClass =
		factory.getOWLClass(localName, prefixManager)

	def getDataProperty(localName: String): OWLDataProperty =
		factory.getOWLDataProperty(localName, prefixManager)

	def getObjectProperty(localName: String): OWLObjectProperty =
		factory.getOWLObjectProperty(localName, prefixManager)

	given envriConfs: EnvriConfigs = Map(Envri.ICOS -> EnvriConfig(
		authHost = "cpauth.icos-cp.eu",
		dataHost = "data.icos-cp.eu",
		metaHost = "meta.icos-cp.eu",
		metaItemPrefix = new URI("http://meta.icos-cp.eu/"),
		dataItemPrefix = new URI("https://meta.icos-cp.eu/"),
		defaultTimezoneId = "UTC"
	))
}
