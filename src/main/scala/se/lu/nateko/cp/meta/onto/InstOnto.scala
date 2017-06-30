package se.lu.nateko.cp.meta.onto

import java.net.URI

import scala.util.Failure
import scala.util.Try
import scala.util.control.NoStackTrace

import org.eclipse.rdf4j.model.Literal
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.query.UpdateExecutionException
import org.semanticweb.owlapi.model.{IRI => OwlIri}

import se.lu.nateko.cp.meta._
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.InstanceServerUtils
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.utils.sesame._
import org.eclipse.rdf4j.model.vocabulary.XMLSchema
import org.eclipse.rdf4j.model.vocabulary.RDFS

class InstOnto (instServer: InstanceServer, val onto: Onto){

	private implicit val factory = instServer.factory

	private val rdfsLabelInfo = DataPropertyDto(
		ResourceDto("label", RDFS.LABEL.toJava, None),
		CardinalityDto(None, None),
		DataRangeDto(XMLSchema.STRING.toJava, Nil)
	)

	private val rdfsCommentInfo = DataPropertyDto(
		ResourceDto("comment", RDFS.COMMENT.toJava, None),
		CardinalityDto(None, None),
		DataRangeDto(XMLSchema.STRING.toJava, Nil)
	)

	def getWriteContext: URI = {
		val writeContexts = instServer.writeContexts
		val nCtxts = writeContexts.length
		assert(nCtxts == 1, s"Expected exactly one write context, found $nCtxts")
		writeContexts.head.toJava
	}

	def getIndividuals(classUri: URI): Seq[ResourceDto] = {
		val labeler = onto.getLabelerForClassIndividuals(classUri)
		instServer.getInstances(classUri.toRdf).map(labeler.getInfo(_, instServer))
	}

	def getRangeValues(individClassUri: URI, propUri: URI): Seq[ResourceDto] = {
		val propInfo = onto.getPropInfo(propUri, individClassUri).asInstanceOf[ObjectPropertyDto]
		val rangeClassUri = propInfo.range.uri
		val rangeClassUris = rangeClassUri +: onto.getSubClasses(rangeClassUri, false).map(_.getIRI.toURI)
		return rangeClassUris.flatMap(getIndividuals)
	}

	def getIndividual(uri: URI): IndividualDto = {
		val labeler = onto.getUniversalLabeler
		val iri = uri.toRdf

		val classInfo: ClassDto = {
			val theType = InstanceServerUtils.getSingleType(iri, instServer)
			val mainInfo = onto.getClassInfo(theType.toJava)
			val extraProps: Seq[PropertyDto] = Seq(rdfsLabelInfo, rdfsCommentInfo)
			mainInfo.copy(properties = extraProps ++ mainInfo.properties)
		}

		val values: Seq[ValueDto] = instServer.getStatements(iri).collect{
			case SesameStatement(_, pred, value: Literal) =>
				val prop = onto.factory.getOWLDataProperty(OwlIri.create(pred.toJava))
				LiteralValueDto(
					value = value.getLabel,
					property = onto.rdfsLabeling(prop)
				)
			case SesameStatement(_, pred, value: IRI)  if(pred != RDF.TYPE) =>
				val prop = onto.factory.getOWLObjectProperty(OwlIri.create(pred.toJava))
				ObjectValueDto(
					value = labeler.getInfo(value, instServer),
					property = onto.rdfsLabeling(prop)
				)
		}

		IndividualDto(
			resource = labeler.getInfo(iri, instServer),
			owlClass = classInfo,
			values = values
		)
	}

	def hasIndividual(uriStr: String): Boolean =
		instServer.hasStatement(Some(factory.createIRI(uriStr)), None, None)

	def createIndividual(uriStr: String, typeUriStr: String): Try[Unit] = {
		if(hasIndividual(uriStr)) Failure(new Exception("Individual already exists!") with NoStackTrace)
		else Try{
			val uri = instServer.factory.createIRI(uriStr)
			val typeUri = instServer.factory.createIRI(typeUriStr)
			instServer.addInstance(uri, typeUri)
		}.flatten
	}

	def deleteIndividual(uriStr: String): Try[Unit] = Try{
		val uri = instServer.factory.createIRI(uriStr)
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
		Some(statement.getSubject.asInstanceOf[IRI]),
		Some(statement.getPredicate),
		Some(statement.getObject)
	)

	private def updateDtoToStatement(update: UpdateDto): Statement = {
		val classUri = InstanceServerUtils.getSingleType(update.subject.toRdf, instServer)

		val obj: Value = getPropInfo(update.predicate, classUri.toJava) match{
			case dp: DataPropertyDto =>
				val dtype = dp.range.dataType
				factory.createLiteral(update.obj, dtype)

			case _: ObjectPropertyDto => factory.createIRI(update.obj)
		}

		factory.createStatement(update.subject.toRdf, update.predicate.toRdf, obj)
	}

	private def getPropInfo(propUri: URI, classUri: URI): PropertyDto =
		propUri.toRdf match {
			case RDFS.LABEL => rdfsLabelInfo
			case RDFS.COMMENT => rdfsCommentInfo
			case _ => onto.getPropInfo(propUri, classUri)
		}

	private def updateDtoToRdfUpdate(update: UpdateDto) =
		RdfUpdate(updateDtoToStatement(update), update.isAssertion)

}
