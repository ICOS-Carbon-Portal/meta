package se.lu.nateko.cp.meta.onto

import scala.language.unsafeNulls

import org.eclipse.rdf4j.model.vocabulary.{OWL, RDF, RDFS, XSD}
import org.eclipse.rdf4j.model.{IRI, Literal, Statement, Value, ValueFactory}
import org.eclipse.rdf4j.query.UpdateExecutionException
import org.semanticweb.owlapi.model.IRI as OwlIri
import se.lu.nateko.cp.meta.*
import se.lu.nateko.cp.meta.instanceserver.{InstanceServer, RdfUpdate, StatementSource}
import se.lu.nateko.cp.meta.utils.rdf4j.*

import java.net.URI
import scala.collection.mutable
import scala.util.control.NoStackTrace
import scala.util.{Failure, Try}

import StatementSource.{getPropValueHolders, getStatements, getTypes}

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

	def getIndividualsSparql(classUri: URI, subjectPrefix: Option[URI]): String =
		val classInfo = onto.getClassInfo(classUri)
		val propUris = (
			classInfo.properties.map {
				case p: DataPropertyDto => p.resource.uri
				case p: ObjectPropertyDto => p.resource.uri
			} ++ Seq(RDFS.LABEL.toJava, RDFS.COMMENT.toJava, RDFS.SEEALSO.toJava)
		).distinct.filterNot(_ == RDF.TYPE.toJava)

		val nextVarName = varNameGenerator()

		val bindings = propUris.map(uri => (uri, nextVarName(sparqlVarBase(uri))))
		val selectVars = ("?s" +: bindings.map{case (_, name) => s"?$name"}).mkString(" ")
		val optionalPatterns = bindings
			.map{case (uri, name) => s"\tOPTIONAL { ?s ${sparqlPredicate(uri)} ?$name . }"}
			.mkString("\n")
		val subjectPrefixFilter = subjectPrefix
			.map(prefix => s"""\tFILTER(STRSTARTS(STR(?s), "${prefix.toString}"))""")
			.getOrElse("")

		s"""PREFIX cpmeta: <${OntoConstants.CpmetaPrefix}>
		|PREFIX rdf: <${RDF.NAMESPACE}>
		|PREFIX rdfs: <${RDFS.NAMESPACE}>
		|
		|SELECT $selectVars
		|WHERE {
		|	?s rdf:type <${classUri.toString}> .
		|$subjectPrefixFilter
		|$optionalPatterns
		|}
		|ORDER BY ?s""".stripMargin

	private def sparqlPredicate(uri: URI): String =
		val str = uri.toString
		val prefixes: Map[String, String] = Map(
			"cpmeta" -> OntoConstants.CpmetaPrefix,
			"rdfs" -> RDFS.NAMESPACE
		)
		prefixes.collectFirst{
			case (prefix, namespace) if str.startsWith(namespace) =>
				val suffix = str.substring(namespace.length)
				if suffix.matches("[A-Za-z_][A-Za-z0-9_\\.-]*") then s"$prefix:$suffix"
				else s"<$str>"
		}.getOrElse(s"<$str>")

	private def varNameGenerator(): String => String =
		val counters = mutable.Map.empty[String, Int]
		base =>
			val next = counters.getOrElse(base, 0) + 1
			counters.update(base, next)
			if next == 1 then base else s"$base$next"

	private def sparqlVarBase(propUri: URI): String =
		val local = Option(propUri.getFragment)
			.orElse(Option(propUri.getPath).flatMap(_.split('/').lastOption))
			.getOrElse("value")
		val withoutHasPrefix =
			if local.startsWith("has") && local.length > 3 && local.charAt(3).isUpper
			then local.substring(3)
			else local
		val lowerCamel = withoutHasPrefix.headOption
			.map(ch => ch.toLower.toString + withoutHasPrefix.substring(1))
			.getOrElse("value")
		val clean = lowerCamel
			.replaceAll("[^A-Za-z0-9_]", "_")
			.replaceAll("^[^A-Za-z_]+", "")
		if clean.isEmpty then "value" else clean

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

	
	private def hasStatement(statement: Statement)(using conn: StatementSource): Boolean =
		statement.getSubject match
			case subj: IRI => conn.hasStatement(subj, statement.getPredicate, statement.getObject)
			case _ => false

	private def updateDtoToStatement(update: UpdateDto)(using StatementSource): Statement =
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

	private def updateDtoToRdfUpdate(update: UpdateDto)(using StatementSource) =
		RdfUpdate(updateDtoToStatement(update), update.isAssertion)

end InstOnto

object InstOnto:
	/**
	 * returns Some type IRI if any, None if none, and throws an AssertionError if the instance has more than one type.
	 * The type owl:NamedIndividual is disregarded.
	 */
	def getSingleTypeIfAny(instUri: IRI)(using StatementSource): Option[IRI] =

		val types = getTypes(instUri).filter(_ != OWL.NAMEDINDIVIDUAL).distinct

		assert(types.size <= 1, s"Expected individual $instUri to have at most one type, but it had ${types.size}")
		types.headOption


	def getSingleType(instUri: IRI)(using StatementSource): IRI =
		val typeIfAny = getSingleTypeIfAny(instUri)
		assert(typeIfAny.isDefined, s"Instance $instUri has no type")
		typeIfAny.get
