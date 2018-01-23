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
import se.lu.nateko.cp.meta.utils.rdf4j._
import org.eclipse.rdf4j.model.ValueFactory

abstract class MetadataUpdater(vocab: CpVocab) {
	import MetadataUpdater._
	import StatementStability._

	protected def stability(sp: SubjPred, hash: Sha256Sum): StatementStability

	def calculateUpdates(hash: Sha256Sum, oldStatements: Seq[Statement], newStatements: Seq[Statement]): Seq[RdfUpdate] = {
		if(oldStatements.isEmpty) newStatements.map(RdfUpdate(_, true))
		else {
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
	}
}

class StaticCollMetadataUpdater(vocab: CpVocab, metaVocab: CpmetaVocab) extends MetadataUpdater(vocab) {
	import MetadataUpdater._
	import StatementStability._

	override protected def stability(sp: SubjPred, hash: Sha256Sum): StatementStability = {
		val pred = sp._2
		if(pred === metaVocab.dcterms.hasPart) Fixed
		else Plain
	}
}

class DobjMetadataUpdater(vocab: CpVocab, metaVocab: CpmetaVocab, sparql: SparqlRunner) extends MetadataUpdater(vocab) {
	import MetadataUpdater._
	import StatementStability._

	override protected def stability(sp: SubjPred, hash: Sha256Sum): StatementStability = {
		val acq = vocab.getAcquisition(hash)
		val subm = vocab.getSubmission(hash)
		val cov = vocab.getSpatialCoverate(hash)
		val (subj, pred) = sp
		val isProvTime = pred === metaVocab.prov.endedAtTime || pred === metaVocab.prov.startedAtTime

		if(subj == acq && isProvTime) Sticky
		else if(pred === metaVocab.hasSpatialCoverage || subj == cov) Sticky
		else if(subj == subm && isProvTime) Fixed
		else if(pred === metaVocab.hasSizeInBytes) Fixed
		else Plain
	}

	def getCurrentStatements(hash: Sha256Sum, server: InstanceServer)(implicit ctxt: ExecutionContext): Future[Seq[Statement]] = {
		val objUri = vocab.getDataObject(hash)
		if(!server.hasStatement(Some(objUri), None, None)) Future.successful(Nil)
		else {
			val fromClauses = server.writeContexts.map(graph => s"FROM <$graph>").mkString("\n")
			val query = SparqlQuery(s"""construct{?s ?p ?o}
				|$fromClauses
				|where{
				|	{
				|		BIND(<$objUri> AS ?s)
				|		?s ?p ?o
				|	} UNION
				|	{
				|		<$objUri> ?p0 ?s .
				|		FILTER(?p0 != <${metaVocab.isNextVersionOf}>)
				|		?s ?p ?o
				|	}
				|}""".stripMargin)
			sparql.evaluateGraphQuery(query).map(_.toIndexedSeq)
		}
	}

}

object MetadataUpdater{

	object StatementStability extends Enumeration{
		type StatementStability = Value
		val Plain, Sticky, Fixed = Value
	}

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
