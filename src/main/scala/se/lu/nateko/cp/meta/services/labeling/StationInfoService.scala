package se.lu.nateko.cp.meta.services.labeling

import scala.util.Try

import org.openrdf.model.Statement
import org.openrdf.model.URI

import se.lu.nateko.cp.cpauth.core.UserInfo
import se.lu.nateko.cp.meta.utils.sesame._
import spray.json.JsObject
import spray.json.JsString

trait StationInfoService { self: StationLabelingService =>

	private val (factory, vocab) = getFactoryAndVocab(server)
	private val protectedPredicates = Set(vocab.hasAssociatedFile, vocab.hasApplicationStatus)

	private val dataTypeInfos = {
		import org.semanticweb.owlapi.model.IRI
		import se.lu.nateko.cp.meta.ClassDto
		import se.lu.nateko.cp.meta.DataPropertyDto

		def toDatatypeLookup(classInfo: ClassDto) =
			classInfo.properties.collect{
				case DataPropertyDto(prop, _, range) => (prop.uri, range.dataType)
			}.toMap

		val stationClass = onto.factory.getOWLClass(IRI.create(vocab.station))
		onto.getBottomSubClasses(stationClass)
			.map(onto.getClassInfo)
			.map(classInfo => (classInfo.resource.uri, toDatatypeLookup(classInfo)))
			.toMap
	}

	def saveStationInfo(info: JsObject, uploader: UserInfo): Unit = {

		val stationUri = info.fields.get("stationUri")
			.collect{case JsString(str) => str}
			.map(factory.createURI).get

		assertThatWriteIsAuthorized(stationUri, uploader)

		val newInfo: Seq[Statement] = for(
			classUri <- lookupStationClass(stationUri).toSeq;
			(fieldName, fieldValue) <- info.fields.collect{case (name, JsString(value)) => (name, value)};
			propUri = vocab.getRelative(fieldName);
			dataType <- lookupDatatype(classUri, propUri).toSeq
		) yield {
			val lit = factory.createLiteral(fieldValue, dataType)
			factory.createStatement(stationUri, propUri, lit)
		}

		val currentInfo = server.getStatements(stationUri)

		server.applyDiff(currentInfo.filter(notProtected), newInfo.filter(notProtected))
	}

	private def lookupDatatype(classUri: java.net.URI, propUri: java.net.URI): Option[URI] =
		dataTypeInfos.get(classUri).flatMap(_.get(propUri)).map(uri => factory.createURI(uri))

	private def notProtected(statement: Statement): Boolean = statement match{
		case SesameStatement(_, pred, _) if protectedPredicates.contains(pred) => false
		case _ => true
	}
}
