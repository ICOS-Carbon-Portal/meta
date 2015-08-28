package se.lu.nateko.cp.meta.test

import org.semanticweb.owlapi.apibinding.OWLManager

import se.lu.nateko.cp.meta.utils.owlapi._
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.SesameInstanceServer
import se.lu.nateko.cp.meta.utils.sesame.Loading

object TestConfig {
	val manager = OWLManager.createOWLOntologyManager
	val factory = manager.getOWLDataFactory
	lazy val owlOnto = getOntologyFromJarResourceFile("/../classes/owl/cpmeta.owl", manager)

	val ontUri = "http://meta.icos-cp.eu/ontologies/cpmeta/instances/"

	lazy val instServer: InstanceServer = {
		val repo = Loading.fromResource("/../classes/owl/cpmetainstances.owl", ontUri)
		new SesameInstanceServer(repo, ontUri)
	}
}