package se.lu.nateko.cp.meta.ingestion.badm

import scala.Iterator

import org.openrdf.model.Statement
import org.openrdf.model.URI
import org.openrdf.model.Value
import org.openrdf.model.ValueFactory
import org.openrdf.model.vocabulary.OWL
import org.openrdf.model.vocabulary.RDF
import org.openrdf.model.vocabulary.RDFS

import BadmSchema.AncillaryValue
import BadmSchema.PropertyInfo
import BadmSchema.Schema
import se.lu.nateko.cp.meta.ingestion.Ingester
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.sesame.EnrichedValueFactory

class RdfBadmSchemaIngester(schema: Schema) extends Ingester{

	import BadmConsts._
	private[this] val specialVars = Set(
		SiteVar, SiteIdVar, SiteNameVar, TeamMemberVar, TeamMemberNameVar, TeamMemberRoleVar,
		TeamMemberEmailVar, TeamMemberInstVar, LocationVar, LocationLatVar, LocationLonVar,
		LocationElevVar, InstrumentVar, VariableVar, VarCodeVar
	)

	def getStatements(f: ValueFactory): Iterator[Statement] = {
		val cpVocab = new CpmetaVocab(f)
		val badmVocab = new BadmVocab(f)

		def getLabelAndComment(uri: URI, label: String, comment: Option[String]): Iterator[(URI, URI, Value)] =
			Iterator((uri, RDFS.LABEL, badmVocab.lit(label))) ++
			comment.map(comm => (uri, RDFS.COMMENT, badmVocab.lit(comm)))

		schema.iterator.filter{
			case (variable, _) => !specialVars.contains(variable)
		}.flatMap{
			case (variable, PropertyInfo(label, comment, None)) =>
				val prop = badmVocab.getDataProp(variable)

				Iterator[(URI, URI, Value)](
					(prop, RDF.TYPE, OWL.DATATYPEPROPERTY),
					(prop, RDFS.SUBPROPERTYOF, cpVocab.hasAncillaryDataValue)
				) ++
				getLabelAndComment(prop, label, comment)

			case (variable, PropertyInfo(label, comment, Some(varVocab))) =>
				val prop = badmVocab.getObjProp(variable)

				Iterator[(URI, URI, Value)](
					(prop, RDF.TYPE, OWL.OBJECTPROPERTY),
					(prop, RDFS.SUBPROPERTYOF, cpVocab.hasAncillaryObjectValue)
				) ++
				getLabelAndComment(prop, label, comment) ++
				varVocab.iterator.flatMap{
					case (badmValue, AncillaryValue(label, comment)) =>
						val value = badmVocab.getVocabValue(variable, badmValue)

						Iterator[(URI, URI, Value)](
							(value, RDF.TYPE, cpVocab.ancillaryValueClass)
						) ++
						getLabelAndComment(value, label, comment)
				}
		}.map(f.tripleToStatement)
	}
}
