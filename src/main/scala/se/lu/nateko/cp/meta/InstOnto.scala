package se.lu.nateko.cp.meta

import java.net.URI

import org.openrdf.model.Literal
import org.openrdf.model.{ URI => SesameURI }
import org.semanticweb.owlapi.model.IRI

import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.labeler.LabelerHelpers
import se.lu.nateko.cp.meta.utils.sesame.EnrichedValueFactory
import se.lu.nateko.cp.meta.utils.sesame.SesameStatement
import se.lu.nateko.cp.meta.utils.sesame.ToJavaUriConverter

class InstOnto (instServer: InstanceServer, onto: Onto){

	def getIndividuals(classUri: URI): Seq[ResourceDto] = {

		val labeler = onto.getLabelerForClassIndividuals(classUri)

		def getForClass(owlClass: URI): Seq[ResourceDto] = {
			val classSesameUri = instServer.factory.createURI(owlClass)
			instServer.getInstances(classSesameUri).map(labeler.getInfo(_, instServer))
		}

		val ownIndividuals = getForClass(classUri)
		val subclassIndividuals = onto.getSubClasses(classUri, false).map(_.getIRI.toURI).flatMap(getForClass)

		(ownIndividuals ++ subclassIndividuals).distinct
	}

	def getIndividual(uri: URI): IndividualDto = {
		val labeler = onto.getUniversalLabeler
		val individual = onto.factory.getOWLNamedIndividual(IRI.create(uri))
		val instUri = instServer.factory.createURI(uri)

		val theType: URI = LabelerHelpers.getSingleType(uri, instServer).toJava
		val classInfo = onto.getClassInfo(theType)

		val values: Seq[ValueDto] = instServer.getStatements(instUri).collect{
			case SesameStatement(_, pred, value: Literal) =>
				val prop = onto.factory.getOWLDataProperty(IRI.create(pred.toJava))
				LiteralValueDto(
					value = value.getLabel,
					property = onto.rdfsLabeling(prop)
				)
			case SesameStatement(_, pred, value: SesameURI) =>
				val prop = onto.factory.getOWLObjectProperty(IRI.create(pred.toJava))
				ObjectValueDto(
					value = labeler.getInfo(instUri, instServer),
					property = onto.rdfsLabeling(prop)
				)
		}

		IndividualDto(
			resource = labeler.getInfo(instUri, instServer),
			owlClass = classInfo,
			values = values
		)
	}

}