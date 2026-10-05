package se.lu.nateko.cp.meta.utils.owlapi

import scala.language.unsafeNulls

import org.semanticweb.owlapi.io.XMLUtils
import org.semanticweb.owlapi.model.*

import java.util.Optional
import java.util.stream.Stream as JavaStream
import scala.reflect.ClassTag
import scala.jdk.CollectionConverters.IteratorHasAsScala

extension [T] (opt: Optional[T])
	def toOption: Option[T] = if(opt.isPresent) Some(opt.get) else None


extension [T <: AnyRef] (stream: JavaStream[T])
	def toIndexedSeq(implicit ev: ClassTag[T]): IndexedSeq[T] = stream.iterator().asScala.toIndexedSeq


// Anchor class for the OWL resources shipped in meta's own src/main/resources/owl/.
// Resource paths passed in are absolute (leading '/'), so the anchor class only needs
// to live on the same classpath, not necessarily next to the resources; using a class
// that actually lives in `meta` (rather than the shared CpmetaConfig, which moved to
// rdfCommon) keeps that assumption self-evidently true regardless of module layout.
private object OwlResourceAnchor

def getOntologyFromJarResourceFile(
		resourcePath: String,
		manager: OWLOntologyManager): OWLOntology = {
	val stream = OwlResourceAnchor.getClass.getResourceAsStream(resourcePath)
	manager.loadOntologyFromOntologyDocument(stream)
}

def getLastFragment(iri: IRI): String = {
	XMLUtils.getNCNameSuffix(iri.toString)
}
