package se.lu.nateko.cp.meta.utils.owlapi


import org.semanticweb.owlapi.model._
import org.semanticweb.owlapi.io.XMLUtils
import java.util.Optional
import java.util.stream.{Stream => JavaStream}
import scala.reflect.ClassTag
import se.lu.nateko.cp.meta.CpmetaConfig

extension [T] (opt: Optional[T])
	def toOption: Option[T] = if(opt.isPresent) Some(opt.get) else None


extension [T <: AnyRef] (stream: JavaStream[T])
	def toIndexedSeq(implicit ev: ClassTag[T]): IndexedSeq[T] = stream.toArray[T](Array.ofDim[T]).toIndexedSeq


def getOntologyFromJarResourceFile(
		resourcePath: String,
		manager: OWLOntologyManager): OWLOntology = {
	val stream = CpmetaConfig.getClass.getResourceAsStream(resourcePath)
	manager.loadOntologyFromOntologyDocument(stream)
}

def getLastFragment(iri: IRI): String = {
	XMLUtils.getNCNameSuffix(iri.toString)
}
