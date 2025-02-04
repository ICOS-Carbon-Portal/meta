package se.lu.nateko.cp.meta.onto

import org.eclipse.rdf4j.model.vocabulary.{OWL, RDF, RDFS, XSD}
import org.eclipse.rdf4j.model.{IRI, Literal, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.UpdateExecutionException
import org.semanticweb.owlapi.model.IRI as OwlIri
import se.lu.nateko.cp.meta.*
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, RdfUpdate, TriplestoreConnection}
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import scala.util.control.NoStackTrace
import scala.util.{Failure, Try}

import TriplestoreConnection.*

class InstOnto (instServer: InstanceServer, val onto: Onto):

	private given factory: ValueFactory = instServer.factory

	private val rdfsLabelInfo = DataPropertyDto(
		ResourceDto("label", RDFS.LABEL.toJava, None),
		CardinalityDto(None, None),
		DataRangeDto(XSD.STRING.toJava, Nil)
	)

	private val rdfsCommentInfo = DataPropertyDto(
		ResourceDto("comment", RDFS.COMMENT.toJava, None),
		CardinalityDto(None, None),
		DataRangeDto(XSD.STRING.toJava, Nil)
	)

	private val rdfsSeeAlsoInfo = DataPropertyDto(
		ResourceDto("seeAlso", RDFS.SEEALSO.toJava, None),
		CardinalityDto(None, None),
		DataRangeDto(XSD.ANYURI.toJava, Nil)
	)

	def getWriteContext: URI = instServer.writeContext.toJava

	def getIndividuals(classUri: URI): Seq[ResourceDto] = instServer.access:
		val labeler = onto.getLabelerForClassIndividuals(classUri)
		getPropValueHolders(RDF.TYPE, classUri.toRdf).map(labeler.getInfo)


	def getRangeValues(individClassUri: URI, propUri: URI): Seq[ResourceDto] = {
		assert(individClassUri != null)//just to silence the not-used warning;
		//class uri will be needed in the future for better class-specific range calculation
		val rangeClassUris = onto.getObjPropRangeClassUnion(propUri)
		rangeClassUris.flatMap(getIndividuals)
	}

	def getIndividual(uri: URI): IndividualDto = instServer.access:
		val labeler = onto.getUniversalLabeler
		val iri = uri.toRdf

		val classInfo: ClassDto = {
			val theType = InstOnto.getSingleType(iri)
			val mainInfo = onto.getClassInfo(theType.toJava)
			val extraProps: Seq[PropertyDto] = Seq(rdfsLabelInfo, rdfsCommentInfo, rdfsSeeAlsoInfo)
			mainInfo.copy(properties = extraProps ++ mainInfo.properties)
		}

		val values: Seq[ValueDto] = getStatements(iri).collect{
			case Rdf4jStatement(_, pred, value: Literal) =>
				val prop = onto.factory.getOWLDataProperty(OwlIri.create(pred.toJava))
				LiteralValueDto(
					value = value.getLabel,
					property = onto.rdfsLabeling(prop)
				)

			//rdfs:seeAlso is special: anyURI literal on the front end, Resource on the back end
			case Rdf4jStatement(_, RDFS.SEEALSO, value: IRI) =>
				LiteralValueDto(
					value = value.stringValue,
					property = rdfsSeeAlsoInfo.resource
				)

			case Rdf4jStatement(_, pred, value: IRI)  if(pred != RDF.TYPE) =>
				val prop = onto.factory.getOWLObjectProperty(OwlIri.create(pred.toJava))
				ObjectValueDto(
					value = labeler.getInfo(value),
					property = onto.rdfsLabeling(prop)
				)
		}

		IndividualDto(
			resource = labeler.getInfo(iri),
			owlClass = classInfo,
			values = values
		)
	end getIndividual

	def hasIndividual(uriStr: String): Boolean = instServer.access: conn ?=>
		conn.hasStatement(factory.createIRI(uriStr), RDF.TYPE, null)

	def createIndividual(uriStr: String, typeUriStr: String): Try[Unit] = {
		if(hasIndividual(uriStr)) Failure(new Exception("Individual already exists!") with NoStackTrace)
		else Try{
			val uri = factory.createIRI(uriStr)
			val typeUri = factory.createIRI(typeUriStr)
			instServer.addInstance(uri, typeUri)
		}.flatten
	}

	def deleteIndividual(uriStr: String): Try[Unit] = Try:
		val uri = factory.createIRI(uriStr)
		val toRemove = instServer.access:
			getStatements(uri) ++ getStatements(null, null, uri)
		instServer.removeAll(toRemove)


	def performReplacement(replacement: ReplaceDto): Try[Unit] = instServer.access:
		val updates = Try{
			val assertion: RdfUpdate = updateDtoToRdfUpdate(replacement.assertion)
			val retraction: RdfUpdate = updateDtoToRdfUpdate(replacement.retraction)

			if(!hasStatement(retraction.statement)) throw new UpdateExecutionException(
				"Database does not contain the statement to retract during the requested replacement."
			)
			Seq(retraction, assertion)
		}
		updates.flatMap(instServer.applyAll(_)())


	def applyUpdates(updates: Seq[UpdateDto]): Try[Unit] =
		val rdfUpdates = instServer.access:
			Try(updates.map(updateDtoToRdfUpdate))
		rdfUpdates.flatMap(instServer.applyAll(_)())

	
	private def hasStatement(statement: Statement)(using conn: TSC): Boolean =
		statement.getSubject match
			case subj: IRI => conn.hasStatement(subj, statement.getPredicate, statement.getObject)
			case _ => false

	private def updateDtoToStatement(update: UpdateDto)(using TSC): Statement =
		val classUri = InstOnto.getSingleType(update.subject.toRdf)

		val obj: Value = getPropInfo(update.predicate, classUri.toJava) match{
			case dp: DataPropertyDto =>
				val dtype = dp.range.dataType
				factory.createLiteral(update.obj, dtype)

			case _: ObjectPropertyDto => factory.createIRI(update.obj)
		}

		factory.createStatement(update.subject.toRdf, update.predicate.toRdf, obj)


	private def getPropInfo(propUri: URI, classUri: URI): PropertyDto =
		propUri.toRdf match {
			case RDFS.LABEL => rdfsLabelInfo
			case RDFS.COMMENT => rdfsCommentInfo
			//rdfs:seeAlso is special: anyURI literal on the front end, Resource on the back end
			case RDFS.SEEALSO => ObjectPropertyDto(rdfsSeeAlsoInfo.resource, rdfsSeeAlsoInfo.cardinality)
			case _ => onto.getPropInfo(propUri, classUri)
		}

	private def updateDtoToRdfUpdate(update: UpdateDto)(using TSC) =
		RdfUpdate(updateDtoToStatement(update), update.isAssertion)

end InstOnto

object InstOnto:
	/**
	 * returns Some type IRI if any, None if none, and throws an AssertionError if the instance has more than one type.
	 * The type owl:NamedIndividual is disregarded.
	 */
	def getSingleTypeIfAny(instUri: IRI)(using TSC): Option[IRI] =

		val types = getTypes(instUri).filter(_ != OWL.NAMEDINDIVIDUAL).distinct

		assert(types.size <= 1, s"Expected individual $instUri to have at most one type, but it had ${types.size}")
		types.headOption


	def getSingleType(instUri: IRI)(using TSC): IRI =
		val typeIfAny = getSingleTypeIfAny(instUri)
		assert(typeIfAny.isDefined, s"Instance $instUri has no type")
		typeIfAny.get
