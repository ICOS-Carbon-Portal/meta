package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.query.algebra._

import se.lu.nateko.cp.meta.services.CpmetaVocab

class TempCoveragePatternSearch(meta: CpmetaVocab){
	import PatternFinder._

	// ?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart
	val startTimePattern = search(meta.prov.startedAtTime, meta.hasStartTime)

	// ?dobj cpmeta:hasEndTime | (cpmeta:wasAcquiredBy / prov:endedAtTime) ?timeEnd
	val endTimePattern = search(meta.prov.endedAtTime, meta.hasEndTime)

	private def search(l2Pred: IRI, l3Pred: IRI): TopNodeSearch[TempCoveragePattern] = {

		val startTimeL3: TopNodeSearch[TimePatternVars] = takeNode.ifIs[StatementPattern].thenSearch{sp =>
			val (s, p, o) = splitTriple(sp)
			if(l3Pred == p.getValue && !s.isAnonymous && !o.isAnonymous)
				Some(new TimePatternVars(s.getName, o.getName))
			else
				None
		}

		val isStPat = takeNode.ifIs[StatementPattern]

		val startTimeL2: TopNodeSearch[TimePatternVars] = takeNode.ifIs[Join].thenSearch{join =>
			for(
				left <- isStPat(join.getLeftArg);
				right <- isStPat(join.getRightArg);
				(sl, pl, ol) = splitTriple(left);
				(sr, pr, or) = splitTriple(right)
				if meta.wasAcquiredBy == pl.getValue && l2Pred == pr.getValue && ol.getName == sr.getName &&
					!sl.isAnonymous && !or.isAnonymous
			) yield
				new TimePatternVars(sl.getName, or.getName)
		}

		takeNode.ifIs[Union].thenSearch(union => {
			for(
				l2 <- inUnion(startTimeL2)(union);
				l3 <- inUnion(startTimeL3)(union) if l3 == l2
			) yield
				new TempCoveragePattern(union, l2.dobjVar, l3.timeVar)
		})
	}.recursive

	def inUnion[O](inner: TopNodeSearch[O]): NodeSearch[Union, O] =
		u => inner(u.getLeftArg).orElse(inner(u.getRightArg))

}

private case class TimePatternVars(val dobjVar: String, val timeVar: String)

class TempCoveragePattern(val expr: Union, val dobjVar: String, val timeVar: String)
