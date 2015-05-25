package se.lu.nateko.cp.meta

import java.net.URI
import scala.collection.JavaConversions._
import Utils._
import org.semanticweb.owlapi.model.AxiomType
import org.semanticweb.owlapi.model.IRI
import org.semanticweb.owlapi.model.OWLDataProperty
import org.semanticweb.owlapi.model.OWLEntity
import org.semanticweb.owlapi.model.OWLNamedIndividual
import org.semanticweb.owlapi.model.OWLOntology
import org.semanticweb.owlapi.model.OWLOntologyManager
import org.semanticweb.owlapi.search.EntitySearcher
import org.semanticweb.owlapi.model.OWLObjectProperty
import org.semanticweb.owlapi.model.OWLClass
import org.semanticweb.owlapi.model.OWLProperty
import org.semanticweb.owlapi.model.OWLDatatype
import org.semanticweb.owlapi.model.OWLDatatypeRestriction
import org.semanticweb.owlapi.vocab.OWLFacet
import org.semanticweb.owlapi.model.OWLDataOneOf


class Onto (ontology: OWLOntology) extends java.io.Closeable{

	private val factory = ontology.getOWLOntologyManager.getOWLDataFactory
	private val reasoner: Reasoner = new HermitBasedReasoner(ontology)

	val rdfsLabeling: OWLEntity => ResourceDto =
		Labeler.rdfs.getInfo(_, ontology)

	override def close(): Unit = {
		reasoner.close()
	}

	def getExposedClasses: Seq[ResourceDto] =
		ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION).toIterable
			.filter(axiom =>
				axiom.getProperty == Vocab.exposedToUsersAnno && {
					axiom.getValue.asLiteral.toOption match {
						case Some(owlLit) if(owlLit.isBoolean) => owlLit.parseBoolean
						case _ => false
					}
				}
			)
			.map(_.getSubject)
			.collect{case iri: IRI => factory.getOWLClass(iri)}
			.map(rdfsLabeling)
			.toSeq
			.distinct

	//TODO Cache this method
	def getLabelerForClassIndividuals(classUri: URI): Labeler[OWLNamedIndividual] = {
		val owlClass = factory.getOWLClass(IRI.create(classUri))

		val displayProp: Option[OWLDataProperty] =
			EntitySearcher.getAnnotations(owlClass, ontology, Vocab.displayPropAnno)
				.toIterable
				.map(_.getValue.asIRI.toOption)
				.collect{case Some(iri) => factory.getOWLDataProperty(iri)}
				.filter(ontology.isDeclared)
				.headOption

		displayProp match{
			case Some(prop) => Labeler.singleProp(prop)
			case None => Labeler.rdfs
		}
	}

	def getUniversalLabeler: Labeler[OWLNamedIndividual] = ???

	def getTopLevelClasses: Seq[ResourceDto] = reasoner.getTopLevelClasses.map(rdfsLabeling)

	def getClassInfo(classUri: URI): ClassDto = {
		val owlClass = factory.getOWLClass(IRI.create(classUri))
		ClassDto(
			resource = rdfsLabeling(owlClass),
			properties = reasoner.getPropertiesWhoseDomainIncludes(owlClass).collect{
				case op: OWLObjectProperty => getPropInfo(op, owlClass)
				case dp: OWLDataProperty => getPropInfo(dp, owlClass)
			}
		)
	}
	
	def getPropInfo(prop: OWLObjectProperty, ctxt: OWLClass): ObjectPropertyDto = {
		val ranges = EntitySearcher.getRanges(prop, ontology).collect{
			case owlClass: OWLClass => rdfsLabeling(owlClass)
		}.toSeq

		assert(ranges.size == 1, "Only single object property ranges, of the simple OWLClass kind, are supported at the moment")

		ObjectPropertyDto(
			resource = rdfsLabeling(prop),
			cardinality = getCardinalityInfo(prop, ctxt),
			range = ranges.head
		)
	}
	
	def getPropInfo(prop: OWLDataProperty, ctxt: OWLClass): DataPropertyDto = {
		val ranges: Seq[DataRangeDto] = EntitySearcher.getRanges(prop, ontology).collect{
			case dt: OWLDatatype => DataRangeDto(dt.getIRI.toURI, Nil)

			case dtr: OWLDatatypeRestriction =>
				val restrictions = dtr.getFacetRestrictions.collect{
					case r if(r.getFacet == OWLFacet.MIN_INCLUSIVE) => MinRestrictionDto(r.getFacetValue.parseDouble)
					case r if(r.getFacet == OWLFacet.MAX_INCLUSIVE) => MaxRestrictionDto(r.getFacetValue.parseDouble)
					case r if(r.getFacet == OWLFacet.PATTERN) => RegexpRestrictionDto(r.getFacetValue.getLiteral)
				}.toSeq
				DataRangeDto(dtr.getDatatype.getIRI.toURI, restrictions)

			case oneOf: OWLDataOneOf =>
				val literals = oneOf.getValues.toSeq
				val dtypes = literals.map(_.getDatatype).distinct
				assert(dtypes.length == 1, "Expecting 1 or more literals of same data type in OneOf data range definition")

				val restriction = OneOfRestrictionDto(literals.map(_.getLiteral))
				DataRangeDto(dtypes.head.getIRI.toURI, Seq(restriction))
		}.toSeq

		assert(ranges.size == 1, "Only single data ranges are supported for data properties at the moment")

		DataPropertyDto(
			resource = rdfsLabeling(prop),
			cardinality = getCardinalityInfo(prop, ctxt),
			range = ranges.head
		)
	}
	
	def getCardinalityInfo(prop: OWLProperty, ctxt: OWLClass): CardinalityDto = {
		???
	}
	
}