package se.lu.nateko.cp.meta.services.upload

import eu.icoscp.envri.Envri
import org.eclipse.rdf4j.model.vocabulary.RDF
import org.eclipse.rdf4j.model.{IRI, Resource, Statement, ValueFactory}
import se.lu.nateko.cp.meta.api.RdfLens.CollConn
import se.lu.nateko.cp.meta.api.{SparqlQuery, SparqlRunner}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.StatementSource.{getStatements, getUriValues}
import se.lu.nateko.cp.meta.instanceserver.{RdfUpdate, StatementSource, TriplestoreConnection}
import se.lu.nateko.cp.meta.services.{CpVocab, CpmetaVocab}
import se.lu.nateko.cp.meta.utils.rdf4j.*

abstract class MetadataUpdater(vocab: CpVocab):
	import MetadataUpdater.*
	import StatementStability.*

	protected def stability(sp: SubjPred, hash: Sha256Sum)(using Envri): StatementStability

	def calculateUpdates(
		hash: Sha256Sum, oldStatements: Seq[Statement], newStatements: Seq[Statement]
	)(using Envri, TriplestoreConnection): Seq[RdfUpdate] =

		val statDiff = if(oldStatements.isEmpty) newStatements.map(RdfUpdate(_, true)) else {
			val oldBySp = new BySubjPred(oldStatements)
			val newBySp = new BySubjPred(newStatements)
			val allSps = (oldBySp.sps ++ newBySp.sps).toSeq

			allSps.flatMap(sp => stability(sp, hash) match {
				case Fixed =>
					if(oldBySp(sp).isEmpty) newBySp(sp).map(RdfUpdate(_, true))
					else Nil

				case Sticky if(newBySp(sp).isEmpty) =>
					Nil

				case _ =>
					diff(oldBySp(sp), newBySp(sp), vocab.factory)
			})
		}
		val ret = statDiff.filter{//keep only the effectful ones
			case RdfUpdate(Rdf4jStatement(s, p, o), isAssertion) =>
				isAssertion != summon[TriplestoreConnection].hasStatement(s, p, o)
			case _ => true
		}

		ret
	end calculateUpdates
end MetadataUpdater

class StaticCollMetadataUpdater(vocab: CpVocab, metaVocab: CpmetaVocab) extends MetadataUpdater(vocab):
	import MetadataUpdater.*
	import StatementStability.*

	override protected def stability(sp: SubjPred, hash: Sha256Sum)(using Envri): StatementStability =
		val (subj, pred) = sp
		def subjIsSpatCov = subj match
			case s: IRI if s === vocab.getSpatialCoverage(hash) => true
			case _ => false

		if pred === metaVocab.dcterms.hasPart then Fixed
		else if subjIsSpatCov then Sticky
		else Plain

	def getCurrentStatements(collIri: IRI)(using CollConn): IndexedSeq[Statement] =
		getStatements(collIri, null, null).toIndexedSeq ++
		getUriValues(collIri, metaVocab.hasSpatialCoverage).flatMap: covIri =>
			getStatements(covIri, null, null)

end StaticCollMetadataUpdater


class ObjMetadataUpdater(vocab: CpVocab, metaVocab: CpmetaVocab) extends MetadataUpdater(vocab):
	import MetadataUpdater.*
	import StatementStability.*

	private val stickyPredicates =
		import metaVocab.*
		Seq(
			hasNumberOfRows, hasActualColumnNames, hasMinValue, hasMaxValue,
			hasSpatialCoverage, asGeoJSON, RDF.TYPE
		)

	override protected def stability(sp: SubjPred, hash: Sha256Sum)(using Envri): StatementStability =
		val acq = vocab.getAcquisition(hash)
		val subm = vocab.getSubmission(hash)
		val (subj, pred) = sp
		val isProvStartTime = pred === metaVocab.prov.startedAtTime
		val isProvTime = pred === metaVocab.prov.endedAtTime || isProvStartTime

		if(subj == acq && isProvTime) Sticky
		else if(subj == subm && isProvStartTime) Fixed
		else if(subj == subm && isProvTime) Sticky
		else if(pred === metaVocab.hasSizeInBytes) Fixed
		else if(stickyPredicates.contains(pred)) Sticky
		else Plain


	def getCurrentStatements(hash: Sha256Sum)(using envri: Envri, conn: TriplestoreConnection, sp: SparqlRunner): Seq[Statement] =
		val objUri = vocab.getStaticObject(hash)
		if !conn.hasStatement(objUri, null, null) then Nil
		else
			val query = SparqlQuery(s"""construct{?s ?p ?o}
				|FROM <${conn.primaryContext}>
				|where{
				|{
				|	{
				|		BIND(<$objUri> AS ?s)
				|		?s ?p ?o .
				|	} UNION {
				|		<$objUri> ?p0 ?s .
				|		FILTER(?p0 != <${metaVocab.isNextVersionOf}>)
				|		?s ?p ?o
				|	} UNION {
				|		<$objUri> <${metaVocab.wasProducedBy}> ?prod .
				|		?prod <${metaVocab.wasParticipatedInBy}> ?s .
				|		?s a rdf:Seq .
				|		?s ?p ?o
				|	} UNION {
				|		?s <${metaVocab.dcterms.hasPart}> <$objUri> ;
				|			a <${metaVocab.plainCollectionClass}> ;
				|			<${metaVocab.isNextVersionOf}> [] .
				|		?s ?p ?o .
				|		FILTER (
				|			NOT EXISTS {
				|				?s <${metaVocab.dcterms.hasPart}> ?anotherNextVers
				|				FILTER(?anotherNextVers != <$objUri>)
				|			} || ?p = <${metaVocab.dcterms.hasPart}> && ?o = <$objUri>
				|		)
				|	}
				|}
				|	FILTER(?p not in (<${metaVocab.hasBiblioInfo}>, <${metaVocab.hasCitationString}>))
				|}""".stripMargin)
			
			sp.evaluateGraphQuery(query).toIndexedSeq
		end if
	end getCurrentStatements

end ObjMetadataUpdater

object MetadataUpdater{

	enum StatementStability:
		case Plain, Sticky, Fixed

	type SubjPred = (Resource, IRI)

	def diff(dirtyOlds: Seq[Statement], news: Seq[Statement], factory: ValueFactory): Seq[RdfUpdate] = {

		val olds = dirtyOlds.map(s => factory.createStatement(s.getSubject, s.getPredicate, s.getObject))

		olds.diff(news).map(RdfUpdate(_, false)) ++
		news.diff(olds).map(RdfUpdate(_, true))
	}

	private class BySubjPred(stats: Seq[Statement]){

		private val bySp = stats.groupBy(s => (s.getSubject, s.getPredicate))

		def apply(sp: SubjPred): Seq[Statement] = bySp.getOrElse(sp, Nil)

		def sps: Set[SubjPred] = bySp.keySet
	}

}
