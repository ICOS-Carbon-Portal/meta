package se.lu.nateko.cp.meta.onto

import java.net.URI

import scala.collection.concurrent.TrieMap

import org.semanticweb.owlapi.model._
import org.semanticweb.owlapi.model.parameters.Imports
import org.semanticweb.owlapi.search.EntitySearcher
import org.semanticweb.owlapi.vocab.OWLFacet

import se.lu.nateko.cp.meta._
import se.lu.nateko.cp.meta.onto.labeler._
import se.lu.nateko.cp.meta.onto.reasoner._
import se.lu.nateko.cp.meta.utils.owlapi._


class Onto (owlOntology: OWLOntology) extends java.io.Closeable{

	val factory = owlOntology.getOWLOntologyManager.getOWLDataFactory
	private def ontologies = owlOntology.importsClosure
	private val reasoner: Reasoner = new HermitBasedReasoner(owlOntology)
	private val labeler = new UniversalLabeler(owlOntology)
	private val classDtoCache = TrieMap.empty[URI, ClassDto]
	private val propDtoCache = TrieMap.empty[(URI, URI), PropertyDto]

	def rdfsLabeling(entity: OWLEntity): ResourceDto =
		Labeler.getInfo(entity, owlOntology)

	override def close(): Unit = {
		reasoner.close()
	}

	def getExposedClasses: Seq[ClassInfoDto] =
		owlOntology.axioms(AxiomType.ANNOTATION_ASSERTION, Imports.INCLUDED)
			.filter(axiom =>
				axiom.getProperty == Vocab.exposedToUsersAnno && {
					axiom.getValue.asLiteral.toOption match {
						case Some(owlLit) if(owlLit.isBoolean) => owlLit.parseBoolean
						case _ => false
					}
				}
			)
			.map[OWLAnnotationSubject](_.getSubject)
			.toIndexedSeq
			.collect{case iri: IRI =>
				val owlClass = factory.getOWLClass(iri)
				val res = rdfsLabeling(owlClass)
				val newInstBaseUri = EntitySearcher
					.getAnnotations(owlClass, ontologies, Vocab.newInstanceBaseUriAnno)
					.map[OWLAnnotationValue](_.getValue)
					.toIndexedSeq
					.collect{
						case iri: IRI => iri.toURI
					}.headOption
				ClassInfoDto(res.displayName, res.uri, newInstBaseUri)
			}
			.distinct

	def getLabelerForClassIndividuals(classUri: URI): InstanceLabeler = {
		val owlClass = factory.getOWLClass(IRI.create(classUri))
		ClassIndividualsLabeler(owlClass, owlOntology, labeler)
	}

	def getUniversalLabeler: InstanceLabeler = labeler

	def getTopLevelClasses: Seq[OWLClass] = reasoner.getTopLevelClasses

	def getSubClasses(classUri: URI, direct: Boolean): Seq[OWLClass] = {
		val owlClass = factory.getOWLClass(IRI.create(classUri))
		reasoner.getSubClasses(owlClass, direct)
	}

	def getBottomSubClasses(owlClass: OWLClass): Seq[OWLClass] = {
		val directSubs = reasoner.getSubClasses(owlClass, false).filterNot(_.isOWLNothing)
		if(directSubs.isEmpty) Seq(owlClass)
		else directSubs.flatMap(getBottomSubClasses)
	}

	def getClassInfo(classUri: URI): ClassDto =
		getClassInfo(factory.getOWLClass(IRI.create(classUri)))

	def getClassInfo(owlClass: OWLClass): ClassDto = classDtoCache.getOrElseUpdate(
		owlClass.getIRI.toURI,
		ClassDto(
			resource = rdfsLabeling(owlClass),
			properties = reasoner.getPropertiesWhoseDomainIncludes(owlClass).collect{
				case op: OWLObjectProperty => getObjPropInfo(op, owlClass)
				case dp: OWLDataProperty => getDataPropInfo(dp, owlClass)
			}
		)
	)

	def getPropInfo(propUri: URI, classUri: URI): PropertyDto = propDtoCache.getOrElseUpdate(
		(propUri, classUri), {
			val iri = IRI.create(propUri)
			val owlClass = factory.getOWLClass(IRI.create(classUri))

			if(owlOntology.containsDataPropertyInSignature(iri, Imports.INCLUDED))
				getDataPropInfo(factory.getOWLDataProperty(iri), owlClass)

			else if(owlOntology.containsObjectPropertyInSignature(iri, Imports.INCLUDED))
				getObjPropInfo(factory.getOWLObjectProperty(iri), owlClass)

			else throw new OWLRuntimeException(s"No object- or data property has URI $propUri")
		}
	)

	//TODO Take property restrictions into account for range calculations
	def getObjPropRangeClassUnion(propUri: URI): Seq[URI] = {
		val iri = IRI.create(propUri)

		if(!owlOntology.containsObjectPropertyInSignature(iri, Imports.INCLUDED))
			throw new OWLRuntimeException(s"No object property has URI $propUri")

		val prop = factory.getOWLObjectProperty(iri)

		def unfoldUnionRange(expr: OWLClassExpression): Seq[OWLClass] = expr match {
			case cls: OWLClass =>
				cls +: reasoner.getSubClasses(cls, false)
			case union: OWLObjectUnionOf =>
				union.operands.toIndexedSeq.flatMap(unfoldUnionRange)
			case _ =>
				throw new OWLRuntimeException(s"Property $propUri has unsupported range. Only plain classes and class unions are supported.")
		}

		EntitySearcher.getRanges(prop, ontologies).toIndexedSeq
			.flatMap(unfoldUnionRange)
			.map(_.getIRI.toURI)
	}

	private def getObjPropInfo(prop: OWLObjectProperty, ctxt: OWLClass) = ObjectPropertyDto(
		resource = rdfsLabeling(prop),
		cardinality = getCardinalityInfo(prop, ctxt)
	)

	
	private def getDataPropInfo(prop: OWLDataProperty, ctxt: OWLClass): DataPropertyDto = {
		val ranges: Seq[DataRangeDto] = EntitySearcher.getRanges(prop, ontologies).toIndexedSeq.collect{
			case dt: OWLDatatype => DataRangeDto(dt.getIRI.toURI, Nil)

			case dtr: OWLDatatypeRestriction =>
				val restrictions = dtr.facetRestrictions.toIndexedSeq.collect{
					case r if(r.getFacet == OWLFacet.MIN_INCLUSIVE) => MinRestrictionDto(r.getFacetValue.parseDouble)
					case r if(r.getFacet == OWLFacet.MAX_INCLUSIVE) => MaxRestrictionDto(r.getFacetValue.parseDouble)
					case r if(r.getFacet == OWLFacet.PATTERN) => RegexpRestrictionDto(r.getFacetValue.getLiteral)
				}
				DataRangeDto(dtr.getDatatype.getIRI.toURI, restrictions)

			case oneOf: OWLDataOneOf =>
				val literals = oneOf.values.toIndexedSeq
				val dtypes = literals.map(_.getDatatype).distinct
				assert(dtypes.length == 1, "Expecting 1 or more literals of same data type in OneOf data range definition")

				val restriction = OneOfRestrictionDto(literals.map(_.getLiteral))
				DataRangeDto(dtypes.head.getIRI.toURI, Seq(restriction))
		}

		assert(ranges.size == 1, s"Got ${ranges.size} data ranges for property ${prop.getIRI} (expecting exactly 1)")

		DataPropertyDto(
			resource = rdfsLabeling(prop),
			cardinality = getCardinalityInfo(prop, ctxt),
			range = ranges.head
		)
	}
	
	private def getCardinalityInfo(prop: OWLProperty, ctxt: OWLClass): CardinalityDto = {

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
