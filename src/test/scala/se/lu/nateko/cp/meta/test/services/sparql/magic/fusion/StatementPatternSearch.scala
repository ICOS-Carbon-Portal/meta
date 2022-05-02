package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra.*
import org.eclipse.rdf4j.model.IRI

object StatementPatternSearch{
	import PatternFinder.*

	def byPredicate(predValue: IRI): TopNodeSearch[StatementPattern] = takeNode
		.ifIs[StatementPattern]
		.filter(sp => predValue == sp.getPredicateVar.getValue)

	val nonAnonymous: NodeSearch[StatementPattern, NamedVarPattern] = sp => {
		val (s, _, o) = splitTriple(sp)
		if(!s.isAnonymous && !o.isAnonymous)
			Some(new NamedVarPattern(sp))
		else
			None
	}


	def twoStepPropPath(pred1: IRI, pred2: IRI): TopNodeSearch[TwoStepPropPath] = {
		def isPropPath(sp1: StatementPattern, sp2: StatementPattern): Boolean = {
			val (s1, _, o1) = splitTriple(sp1)
			val (s2, _, o2) = splitTriple(sp2)
			!s1.isAnonymous && o1.isAnonymous && s2.isAnonymous && o1.getName == s2.getName &&
			(!o2.isAnonymous || o2.hasValue) && areWithinCommonJoin(Seq(sp1, sp2))
		}

		node => byPredicate(pred1)
			.thenAlsoSearch{sp1 =>
				byPredicate(pred2).filter(sp2 => isPropPath(sp1, sp2)).recursive(node)
			}
			.thenGet{
				case (sp1, sp2) => new TwoStepPropPath(sp1, sp2)
			}
			.recursive(node)
	}

	class TwoStepPropPath(val step1: StatementPattern, val step2: StatementPattern){
		def subjVariable: String = step1.getSubjectVar.getName
		def objVariable: String = step2.getObjectVar.getName
	}

	class NamedVarPattern(val sp: StatementPattern){
		def subjVar = sp.getSubjectVar.getName
		def objVar = sp.getObjectVar.getName
	}
}

