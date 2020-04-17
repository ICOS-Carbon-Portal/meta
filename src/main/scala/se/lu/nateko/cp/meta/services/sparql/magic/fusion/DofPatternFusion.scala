package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.sparql.index._
import se.lu.nateko.cp.meta.utils.rdf4j._

import org.eclipse.rdf4j.query.algebra.TupleExpr

import DofPatternFusion._
import org.eclipse.rdf4j.model.IRI

case class FusionResult(fetch: DataObjectFetch, exprsToFuse: Set[TupleExpr])

class DofPatternFusion(meta: CpmetaVocab){

	def findFusions(patt: DofPattern): Seq[FusionResult] = patt match{
		case DofPattern.Empty => Nil

		case pdp @ ProjectionDofPattern(_, _, _, Some(outer)) =>
			findFusions(pdp.copy(outer = None)) ++ findFusions(outer)

		case pdp: ProjectionDofPattern => findFusions(pdp.inner) match{
			case Seq(singleResult) => Seq(addOrderByAndOffset(pdp, singleResult))
			case any => any
		}

		case list: DofPatternList => list.subs.flatMap(findFusions)

		case union: DofPatternUnion =>
			val subs = union.subs.flatMap(findFusions)

			val isValid = subs.forall(dof => dof.fetch.sort.isEmpty && dof.fetch.offset == 0)

			if(isValid){//should always be true in practice, here for extra code robustness and clarity
				val filter = Or(subs.map(_.fetch.filter))
				val exprs = subs.map(_.exprsToFuse).reduce(_ union _)
				Seq(FusionResult(DataObjectFetch(filter, None, 0), exprs))
			} else
				Nil

		case plain: PlainDofPattern => findPlainFusion(plain).toSeq.map{
			case (filter, exprs) => FusionResult(DataObjectFetch(filter, None, 0), exprs)
		}

	}

	def addOrderByAndOffset(pdp: ProjectionDofPattern, inner: FusionResult): FusionResult = ???

	def findPlainFusion(patt: PlainDofPattern): Option[PlainFusionRes] = patt.dobjVar.flatMap{dobjVar =>
		if(patt.varValues.get(dobjVar).flatMap(_.vals).isDefined) None //dobj pre-specified, no need for any magic
		else {
			val specFilter: Option[PlainFusionRes] = for(
				statPatts <- patt.propPaths.get(dobjVar);
				statPat <- statPatts.find(_.pred === meta.hasObjectSpec);
				valInfo <- patt.varValues.get(statPat.targetVar);
				vals <- valInfo.vals
			) yield {
				val iriVals = vals.toSeq.collect{case iri: IRI => iri}
				CategFilter(Spec, iriVals) -> valInfo.providers.toSet
			}
			???
		}
	}

	def getVarPropLookup(patt: PlainDofPattern): Map[String, Property] = {

		def endVarName(steps: IRI*): Option[String] = steps.reverse.toList match{
			case Nil => patt.dobjVar
			case head :: tail => for(
				prev <- endVarName(tail:_*);
				statPatts <- patt.propPaths.get(prev);
				statPat <- statPatts.find(_.pred === head)
			) yield statPat.targetVar
		}

		def propVar(prop: Property, steps: IRI*) = endVarName(steps:_*).map(_ -> prop)

		Seq(
			propVar(DobjUri),
			propVar(Spec           , meta.hasObjectSpec ),
			propVar(FileName       , meta.hasName       ),
			propVar(FileSize       , meta.hasSizeInBytes),
			propVar(Submitter      , meta.wasSubmittedBy , meta.prov.wasAssociatedWith),
			propVar(SubmissionStart, meta.wasSubmittedBy , meta.prov.startedAtTime    ),
			propVar(SubmissionEnd  , meta.wasSubmittedBy , meta.prov.endedAtTime      ),
			propVar(Station        , meta.wasAcquiredBy  , meta.prov.wasAssociatedWith),
			propVar(Site           , meta.wasAcquiredBy  , meta.wasPerformedAt        ),
			propVar(DataStart      , meta.wasAcquiredBy  , meta.prov.startedAtTime    ),
			propVar(DataEnd        , meta.wasAcquiredBy  , meta.prov.endedAtTime      ),
			propVar(SamplingHeight , meta.wasAcquiredBy  , meta.hasSamplingHeight     ),
		).flatten.toMap
	}
}

object DofPatternFusion{
	type PlainFusionRes = (Filter, Set[TupleExpr])

}