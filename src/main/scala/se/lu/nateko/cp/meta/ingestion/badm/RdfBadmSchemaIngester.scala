package se.lu.nateko.cp.meta.ingestion.badm

import scala.Iterator

import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.Value
import org.eclipse.rdf4j.model.ValueFactory
import org.eclipse.rdf4j.model.vocabulary.OWL
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.vocabulary.RDFS

import BadmSchema.AncillaryValue
import BadmSchema.PropertyInfo
import BadmSchema.Schema
import se.lu.nateko.cp.meta.ingestion.Ingester
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.EnrichedValueFactory
import se.lu.nateko.cp.meta.services.CpVocab

class RdfBadmSchemaIngester(schema: => Schema) extends Ingester{

	import BadmConsts._
	private[this] val specialVars = Set(
		SiteVar, SiteIdVar, SiteNameVar, TeamMemberVar, TeamMemberNameVar, TeamMemberRoleVar,
		TeamMemberEmailVar, TeamMemberInstVar, LocationVar, LocationLatVar, LocationLonVar,
		LocationElevVar, InstrumentVar, VariableVar, VarCodeVar
	)

	def getStatements(f: ValueFactory): Iterator[Statement] = {
		val vocab = new CpVocab(f)
		val cpVocab = new CpmetaVocab(f)
		val badmVocab = new BadmVocab(f)

		def getLabelAndComment(uri: IRI, label: String, comment: Option[String]): Iterator[(IRI, IRI, Value)] =
			Iterator((uri, RDFS.LABEL, badmVocab.lit(label))) ++
			comment.map(comm => (uri, RDFS.COMMENT, badmVocab.lit(comm)))

		schema.iterator.collect{
			case (variable, propInfo) if !specialVars.contains(variable) =>
				propInfo match {
					case PropertyInfo(label, comment, None) =>
						val prop = badmVocab.getDataProp(variable)

						Iterator[(IRI, IRI, Value)](
							(prop, RDF.TYPE, OWL.DATATYPEPROPERTY),
							(prop, RDFS.SUBPROPERTYOF, cpVocab.hasAncillaryDataValue)
						) ++
						getLabelAndComment(prop, label, comment)

					case PropertyInfo(label, comment, Some(varVocab)) =>
						val prop = badmVocab.getObjProp(variable)

						Iterator[(IRI, IRI, Value)](
							(prop, RDF.TYPE, OWL.OBJECTPROPERTY),
							(prop, RDFS.SUBPROPERTYOF, cpVocab.hasAncillaryObjectValue)
						) ++
						getLabelAndComment(prop, label, comment) ++
						varVocab.iterator.flatMap{
							case (badmValue, AncillaryValue(label, comment)) =>
								val value = badmVocab.getVocabValue(variable, badmValue)

								Iterator[(IRI, IRI, Value)](
									(value, RDF.TYPE, cpVocab.ancillaryValueClass)
								) ++
								getLabelAndComment(value, label, comment)
						}
				}
			case (TeamMemberRoleVar, PropertyInfo(_, _, Some(varVocab))) =>
				varVocab.iterator.flatMap{
					case ("PI", _) =>
						Iterator.empty //PI role is common to all ICOS and is declared elsewhere
					case (roleId, AncillaryValue(label, comment)) =>
						val role = vocab.getRole(roleId)
						Iterator[(IRI, IRI, Value)](
							(role, RDF.TYPE, cpVocab.roleClass)
						) ++
						getLabelAndComment(role, label, comment)
				}
		}.flatMap(s => s).map(f.tripleToStatement)
	}
}
