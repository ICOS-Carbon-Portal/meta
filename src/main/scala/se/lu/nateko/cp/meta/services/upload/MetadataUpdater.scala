package se.lu.nateko.cp.meta.services.upload

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.eclipse.rdf4j.model.Resource
import org.eclipse.rdf4j.model.Statement
import org.eclipse.rdf4j.model.IRI

import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.RdfUpdate
import se.lu.nateko.cp.meta.services.CpVocab
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.rdf4j.*
import org.eclipse.rdf4j.model.ValueFactory
import eu.icoscp.envri.Envri

abstract class MetadataUpdater(vocab: CpVocab) {
	import MetadataUpdater.*
	import StatementStability.*

	protected def stability(sp: SubjPred, hash: Sha256Sum)(using Envri): StatementStability

	def calculateUpdates(hash: Sha256Sum, oldStatements: Seq[Statement], newStatements: Seq[Statement], server: InstanceServer)(using Envri): Seq[RdfUpdate] = {
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
		statDiff.filter{//keep only the effectful ones
			case RdfUpdate(Rdf4jStatement(s, p, o), isAssertion) =>
				isAssertion != server.hasStatement(s, p, o)
			case _ => true
		}
	}
}

class StaticCollMetadataUpdater(vocab: CpVocab, metaVocab: CpmetaVocab) extends MetadataUpdater(vocab) {
	import MetadataUpdater.*
	import StatementStability.*

	override protected def stability(sp: SubjPred, hash: Sha256Sum)(implicit envri: Envri): StatementStability = {
		val pred = sp._2
		if(pred === metaVocab.dcterms.hasPart) Fixed
		else Plain
	}
}

class ObjMetadataUpdater(vocab: CpVocab, metaVocab: CpmetaVocab, sparql: SparqlRunner) extends MetadataUpdater(vocab):
	import MetadataUpdater.*
	import StatementStability.*

	private val stickyPredicates = {
		import metaVocab.*
		Seq(hasNumberOfRows, hasActualColumnNames, hasMinValue, hasMaxValue)
	}

	override protected def stability(sp: SubjPred, hash: Sha256Sum)(implicit envri: Envri): StatementStability = {
		val acq = vocab.getAcquisition(hash)
		val subm = vocab.getSubmission(hash)
		val (subj, pred) = sp
		val isProvStartTime = pred === metaVocab.prov.startedAtTime
		val isProvTime = pred === metaVocab.prov.endedAtTime || isProvStartTime

		if(subj == acq && isProvTime) Sticky
		else if(subj == subm && isProvStartTime) Fixed
		else if(subj == subm && isProvTime) Sticky
		else if(pred === metaVocab.hasSizeInBytes) Fixed
		else if(pred === metaVocab.dcterms.license) Sticky
		else if(stickyPredicates.contains(pred)) Sticky
		else Plain
	}

	def getCurrentStatements(hash: Sha256Sum, server: InstanceServer)(using ExecutionContext, Envri): Future[Seq[Statement]] =
		val objUri = vocab.getStaticObject(hash)
		if !server.hasStatement(Some(objUri), None, None) then Future.successful(Nil)
		else
			val fromClauses = server.writeContexts.map(graph => s"FROM <$graph>").mkString("\n")
			val query = SparqlQuery(s"""construct{?s ?p ?o}
				|$fromClauses
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
				|		?s <${metaVocab.dcterms.hasPart}> <$objUri> ;
				|			a <${metaVocab.plainCollectionClass}> ;
				|			<${metaVocab.isNextVersionOf}> [] .
				|		?s ?p ?o .
				|		FILTER(?p != <${metaVocab.dcterms.hasPart}> || ?o = <$objUri>)
				|	}
				|}
				|	FILTER(?p not in (<${metaVocab.hasBiblioInfo}>, <${metaVocab.hasCitationString}>))
				|}""".stripMargin)
			Future(sparql.evaluateGraphQuery(query).toIndexedSeq)
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
