package se.lu.nateko.cp.meta.utils.owlapi


import org.semanticweb.owlapi.io.XMLUtils
import org.semanticweb.owlapi.model.*
import se.lu.nateko.cp.meta.CpmetaConfig

import java.util.Optional
import java.util.stream.Stream as JavaStream
import scala.reflect.ClassTag

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
