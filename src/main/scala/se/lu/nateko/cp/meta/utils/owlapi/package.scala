package se.lu.nateko.cp.meta.utils


import org.semanticweb.owlapi.model._
import org.semanticweb.owlapi.io.XMLUtils
import java.util.Optional
import java.util.stream.{Stream => JavaStream}
import scala.reflect.ClassTag

package object owlapi {

//	implicit class GoogleScalaOptionable[T](val opt: Optional[T]) extends AnyVal{
//		def toOption: Option[T] = if(opt.isPresent) Some(opt.get) else None
//	}
	implicit class JavaUtilOptionable[T](val opt: Optional[T]) extends AnyVal{
		def toOption: Option[T] = if(opt.isPresent) Some(opt.get) else None
	}

	implicit class JavaStreamToScalaConverter[T <: AnyRef](val stream: JavaStream[T]) extends AnyVal {
		def toIndexedSeq(implicit ev: ClassTag[T]): IndexedSeq[T] = stream.toArray[T](Array.ofDim[T])
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