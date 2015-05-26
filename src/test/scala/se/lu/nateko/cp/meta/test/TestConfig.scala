package se.lu.nateko.cp.meta.test

import org.semanticweb.owlapi.apibinding.OWLManager
import se.lu.nateko.cp.meta.Utils

object TestConfig {
	val manager = OWLManager.createOWLOntologyManager
	val factory = manager.getOWLDataFactory
	lazy val owlOnto = Utils.getOntologyFromJarResourceFile("/../classes/owl/cpmeta.owl", manager)
	lazy val owlInstOnto = Utils.getOntologyFromJarResourceFile("/../classes/owl/content_examples.owl", manager)
}