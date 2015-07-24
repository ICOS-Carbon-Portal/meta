package se.lu.nateko.cp.meta.test

import org.semanticweb.owlapi.apibinding.OWLManager

import se.lu.nateko.cp.meta.Utils
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import se.lu.nateko.cp.meta.utils.sesame.Loading

object TestConfig {
	val manager = OWLManager.createOWLOntologyManager
	val factory = manager.getOWLDataFactory
	lazy val owlOnto = Utils.getOntologyFromJarResourceFile("/../classes/owl/cpmeta.owl", manager)

	lazy val instServer: InstanceServer = {
		val ontUri = "http://meta.icos-cp.eu/ontologies/cpmeta/contentexamples/"
		val repo = Loading.fromResource("/../classes/owl/content_examples.owl", ontUri)
		new SesameInstanceServer(repo, ontUri)
	}
}