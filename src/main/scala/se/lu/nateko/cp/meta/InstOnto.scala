package se.lu.nateko.cp.meta

import java.net.URI
import org.openrdf.model.Literal
import org.openrdf.model.{ URI => SesameURI }
import org.openrdf.model.Value
import org.semanticweb.owlapi.model.IRI
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.labeler.LabelerHelpers
import se.lu.nateko.cp.meta.utils.sesame._
import scala.util.Try
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import org.openrdf.model.Statement
import org.openrdf.query.UpdateExecutionException
import scala.util.Failure
import scala.util.control.NoStackTrace
import org.openrdf.model.vocabulary.RDF

class InstOnto (instServer: InstanceServer, val onto: Onto){

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

	def getRangeValues(individClassUri: URI, propUri: URI): Seq[ResourceDto] = {
		val propInfo = onto.getPropInfo(propUri, individClassUri).asInstanceOf[ObjectPropertyDto]
		val rangeClassUri = propInfo.range.uri
		val rangeClassUris = rangeClassUri +: onto.getSubClasses(rangeClassUri, false).map(_.getIRI.toURI.toURL.toURI)
		return rangeClassUris.flatMap(getIndividuals)
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
			case SesameStatement(_, pred, value: SesameURI)  if(pred != RDF.TYPE) =>
				val prop = onto.factory.getOWLObjectProperty(IRI.create(pred.toJava))
				ObjectValueDto(
					value = labeler.getInfo(value, instServer),
					property = onto.rdfsLabeling(prop)
				)
		}

		IndividualDto(
			resource = labeler.getInfo(instUri, instServer),
			owlClass = classInfo,
			values = values
		)
	}

	def hasIndividual(uriStr: String): Boolean = {
		val uri = instServer.factory.createURI(uriStr)
		!instServer.getStatements(uri).isEmpty
	}

	def createIndividual(uriStr: String, typeUriStr: String): Try[Unit] = {
		if(hasIndividual(uriStr)) Failure(new Exception("Individual already exists!") with NoStackTrace)
		else Try{
			val uri = instServer.factory.createURI(uriStr)
			val typeUri = instServer.factory.createURI(typeUriStr)
			instServer.addInstance(uri, typeUri)
		}.flatten
	}

	def deleteIndividual(uriStr: String): Try[Unit] = Try{
		val uri = instServer.factory.createURI(uriStr)
		val asSubject = instServer.getStatements(uri)
		val asObject = instServer.getStatements(None, None, Some(uri))
		instServer.removeAll(asSubject ++ asObject)
	}

	def performReplacement(replacement: ReplaceDto): Try[Unit] = {
		val updates = Try{
			val assertion: RdfUpdate = updateDtoToRdfUpdate(replacement.assertion)
			val retraction: RdfUpdate = updateDtoToRdfUpdate(replacement.retraction)

			if(!hasStatement(retraction.statement)) throw new UpdateExecutionException(
				"Database does not contain the statement to retract during the requested replacement."
			)
			Seq(retraction, assertion)
		}
		updates.flatMap(instServer.applyAll)
	}

	def applyUpdates(updates: Seq[UpdateDto]): Try[Unit] = {
		val rdfUpdates = Try(updates.map(updateDtoToRdfUpdate))
		rdfUpdates.flatMap(instServer.applyAll)
	}
	
	private def hasStatement(statement: Statement): Boolean = {

		val stIter = instServer.getStatements(
			Some(statement.getSubject.asInstanceOf[SesameURI]),
			Some(statement.getPredicate),
			Some(statement.getObject)
		)
		val length = stIter.size
		stIter.close() //just in case; should have already closed itself by now
		assert(length <= 1, "An RDF statement cannot be present in the triplestore more than once!")
		length == 1
	}

	private def updateDtoToStatement(update: UpdateDto): Statement = {
		val factory = instServer.factory
		val subj = factory.createURI(update.subject)
		val pred = factory.createURI(update.predicate)
		
		val classUri = LabelerHelpers.getSingleType(update.subject, instServer).toJava

		val obj: Value = onto.getPropInfo(update.predicate, classUri) match{
			case dp: DataPropertyDto =>
				val dtype = dp.range.dataType
				factory.createLiteral(update.obj, dtype)

			case op: ObjectPropertyDto => factory.createURI(update.obj)
		}

		factory.createStatement(subj, pred, obj)
	}

	private def updateDtoToRdfUpdate(update: UpdateDto) =
		RdfUpdate(updateDtoToStatement(update), update.isAssertion)

}