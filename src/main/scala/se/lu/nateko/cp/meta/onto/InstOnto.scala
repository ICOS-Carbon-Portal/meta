package se.lu.nateko.cp.meta.onto

import java.net.URI

import scala.util.Failure
import scala.util.Try
import scala.util.control.NoStackTrace

import org.openrdf.model.Literal
import org.openrdf.model.Statement
import org.openrdf.model.{ URI => SesameURI }
import org.openrdf.model.Value
import org.openrdf.model.vocabulary.RDF
import org.openrdf.query.UpdateExecutionException
import org.semanticweb.owlapi.model.IRI

import se.lu.nateko.cp.meta._
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.InstanceServerUtils
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.utils.sesame._

class InstOnto (instServer: InstanceServer, val onto: Onto){

	private implicit val factory = instServer.factory

	def getWriteContext: URI = {
		val writeContexts = instServer.writeContexts
		val nCtxts = writeContexts.length
		assert(nCtxts == 1, s"Expected exactly one write context, found $nCtxts")
		writeContexts.head
	}

	def getIndividuals(classUri: URI): Seq[ResourceDto] = {
		val labeler = onto.getLabelerForClassIndividuals(classUri)
		instServer.getInstances(classUri).map(labeler.getInfo(_, instServer))
	}

	def getRangeValues(individClassUri: URI, propUri: URI): Seq[ResourceDto] = {
		val propInfo = onto.getPropInfo(propUri, individClassUri).asInstanceOf[ObjectPropertyDto]
		val rangeClassUri = propInfo.range.uri
		val rangeClassUris = rangeClassUri +: onto.getSubClasses(rangeClassUri, false).map(_.getIRI.toURI)
		return rangeClassUris.flatMap(getIndividuals)
	}

	def getIndividual(uri: URI): IndividualDto = {
		val labeler = onto.getUniversalLabeler

		val theType = InstanceServerUtils.getSingleType(uri, instServer)
		val classInfo = onto.getClassInfo(theType)

		val values: Seq[ValueDto] = instServer.getStatements(uri).collect{
			case SesameStatement(_, pred, value: Literal) =>
				val prop = onto.factory.getOWLDataProperty(IRI.create(pred))
				LiteralValueDto(
					value = value.getLabel,
					property = onto.rdfsLabeling(prop)
				)
			case SesameStatement(_, pred, value: SesameURI)  if(pred != RDF.TYPE) =>
				val prop = onto.factory.getOWLObjectProperty(IRI.create(pred))
				ObjectValueDto(
					value = labeler.getInfo(value, instServer),
					property = onto.rdfsLabeling(prop)
				)
		}

		IndividualDto(
			resource = labeler.getInfo(uri, instServer),
			owlClass = classInfo,
			values = values
		)
	}

	def hasIndividual(uriStr: String): Boolean =
		instServer.hasStatement(Some(factory.createURI(uriStr)), None, None)

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
	
	private def hasStatement(statement: Statement): Boolean = instServer.hasStatement(
		Some(statement.getSubject.asInstanceOf[SesameURI]),
		Some(statement.getPredicate),
		Some(statement.getObject)
	)

	private def updateDtoToStatement(update: UpdateDto): Statement = {
		val classUri = InstanceServerUtils.getSingleType(update.subject, instServer)

		val obj: Value = onto.getPropInfo(update.predicate, classUri) match{
			case dp: DataPropertyDto =>
				val dtype = dp.range.dataType
				factory.createLiteral(update.obj, dtype)

			case op: ObjectPropertyDto => factory.createURI(update.obj)
		}

		factory.createStatement(update.subject, update.predicate, obj)
	}

	private def updateDtoToRdfUpdate(update: UpdateDto) =
		RdfUpdate(updateDtoToStatement(update), update.isAssertion)

}