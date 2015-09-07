package se.lu.nateko.cp.meta

import java.net.URI
import scala.collection.JavaConversions._
import se.lu.nateko.cp.meta.reasoner.Reasoner
import se.lu.nateko.cp.meta.reasoner.HermitBasedReasoner
import se.lu.nateko.cp.meta.utils.owlapi._
import org.semanticweb.owlapi.search.EntitySearcher
import org.semanticweb.owlapi.vocab.OWLFacet
import org.semanticweb.owlapi.model._
import se.lu.nateko.cp.meta.labeler.Labeler
import se.lu.nateko.cp.meta.labeler.ClassIndividualsLabeler
import se.lu.nateko.cp.meta.labeler.UniversalLabeler
import se.lu.nateko.cp.meta.labeler.InstanceLabeler
import org.semanticweb.owlapi.model.parameters.Imports


class Onto (owlOntology: OWLOntology) extends java.io.Closeable{

	val factory = owlOntology.getOWLOntologyManager.getOWLDataFactory
	private val ontologies = owlOntology.getImportsClosure
	private val reasoner: Reasoner = new HermitBasedReasoner(owlOntology)
	private val labeler = new UniversalLabeler(owlOntology)

	def rdfsLabeling(entity: OWLEntity): ResourceDto =
		Labeler.getInfo(entity, owlOntology)

	override def close(): Unit = {
		reasoner.close()
	}

	def getExposedClasses: Seq[ResourceDto] =
		owlOntology.getAxioms(AxiomType.ANNOTATION_ASSERTION, Imports.INCLUDED).toIterable
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

	def getLabelerForClassIndividuals(classUri: URI): InstanceLabeler = {
		val owlClass = factory.getOWLClass(IRI.create(classUri))
		ClassIndividualsLabeler(owlClass, owlOntology, labeler)
	}

	def getUniversalLabeler: InstanceLabeler = labeler

	def getTopLevelClasses: Seq[ResourceDto] = reasoner.getTopLevelClasses.map(rdfsLabeling)

	def getSubClasses(classUri: URI, direct: Boolean): Seq[OWLClass] = {
		val owlClass = factory.getOWLClass(IRI.create(classUri))
		reasoner.getSubClasses(owlClass, direct)
	}

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

	def getPropInfo(propUri: URI, classUri: URI): PropertyDto = {
		val iri = IRI.create(propUri)
		val owlClass = factory.getOWLClass(IRI.create(classUri))

		if(owlOntology.containsDataPropertyInSignature(iri, Imports.INCLUDED))
			getPropInfo(factory.getOWLDataProperty(iri), owlClass)

		else if(owlOntology.containsObjectPropertyInSignature(iri, Imports.INCLUDED))
			getPropInfo(factory.getOWLObjectProperty(iri), owlClass)

		else throw new OWLRuntimeException(s"No object- or data property has URI $propUri")
	}

	//TODO Take property restrictions into account for range calculations
	def getPropInfo(prop: OWLObjectProperty, ctxt: OWLClass): ObjectPropertyDto = {
		val ranges = EntitySearcher.getRanges(prop, ontologies).collect{
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
		val ranges: Seq[DataRangeDto] = EntitySearcher.getRanges(prop, ontologies).collect{
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

		assert(ranges.size == 1, s"Got ${ranges.size} data ranges for property ${prop.getIRI} (expecting exactly 1)")

		DataPropertyDto(
			resource = rdfsLabeling(prop),
			cardinality = getCardinalityInfo(prop, ctxt),
			range = ranges.head
		)
	}
	
	def getCardinalityInfo(prop: OWLProperty, ctxt: OWLClass): CardinalityDto = {

		val restrictions = reasoner.getSuperClasses(ctxt, false).filter{
			case oor: OWLObjectRestriction if(oor.getProperty == prop) => true
			case odr: OWLDataRestriction if(odr.getProperty == prop) => true
			case _ => false
		}

		val cardinalityRestrictions = restrictions.collect{
			case _: OWLDataSomeValuesFrom     => CardinalityDto(Some(1), None)
			case _: OWLObjectSomeValuesFrom   => CardinalityDto(Some(1), None)
			case r: OWLDataMinCardinality     => CardinalityDto(Some(r.getCardinality), None)
			case r: OWLObjectMinCardinality   => CardinalityDto(Some(r.getCardinality), None)
			case r: OWLDataMaxCardinality     => CardinalityDto(None, Some(r.getCardinality))
			case r: OWLObjectMaxCardinality   => CardinalityDto(None, Some(r.getCardinality))
			case r: OWLDataExactCardinality   => CardinalityDto(Some(r.getCardinality), Some(r.getCardinality))
			case r: OWLObjectExactCardinality => CardinalityDto(Some(r.getCardinality), Some(r.getCardinality))
		}

		val minCardinality = cardinalityRestrictions.map(_.min).flatten.sorted.lastOption
		val functionalCardinality = if(reasoner.isFunctional(prop)) Some(1) else None
		val maxCardinality = (cardinalityRestrictions.map(_.max) :+ functionalCardinality).flatten.sorted.headOption

		CardinalityDto(minCardinality, maxCardinality)
	}
	
}