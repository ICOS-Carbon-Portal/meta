package se.lu.nateko.cp.meta.utils


import org.semanticweb.owlapi.model._
import org.semanticweb.owlapi.io.XMLUtils
import org.semanticweb.owlapi.search.EntitySearcher
import java.util.Optional

package object owlapi {

//	implicit class GoogleScalaOptionable[T](val opt: Optional[T]) extends AnyVal{
//		def toOption: Option[T] = if(opt.isPresent) Some(opt.get) else None
//	}
	implicit class JavaUtilOptionable[T](val opt: Optional[T]) extends AnyVal{
		def toOption: Option[T] = if(opt.isPresent) Some(opt.get) else None
	}

	def getOntologyFromJarResourceFile(
			resourcePath: String,
			manager: OWLOntologyManager): OWLOntology = {
		val stream = getClass.getResourceAsStream(resourcePath)
		manager.loadOntologyFromOntologyDocument(stream)
	}
	
	def getLastFragment(iri: IRI): String = {
		XMLUtils.getNCNameSuffix(iri.toString)
	}

}