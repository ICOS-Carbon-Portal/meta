package se.lu.nateko.cp.meta

import com.google.common.base.Optional
import org.semanticweb.owlapi.model.OWLOntologyManager
import org.semanticweb.owlapi.model.OWLOntology

object Utils {

	implicit class GoogleScalaOptionable[T](val opt: Optional[T]) extends AnyVal{
		def toOption: Option[T] = if(opt.isPresent) Some(opt.get) else None
	}

	def getOntologyFromJarResourceFile(
			resourcePath: String,
			manager: OWLOntologyManager): OWLOntology = {
		val stream = getClass.getResourceAsStream(resourcePath)
		manager.loadOntologyFromOntologyDocument(stream)
	}

}