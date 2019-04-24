package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra._

import se.lu.nateko.cp.meta.services.CpmetaVocab


class DataObjectFetchPatternSearch(meta: CpmetaVocab){
	import PatternFinder._

	val notExistsSingleStatementPattern: NodeSearch[Filter, StatementPattern] = takeNode[Filter]
		.thenGet(_.getCondition)
		.ifIs[Not]
		.thenGet(_.getArg)
		.ifIs[Exists]
		.thenGet(_.getSubQuery)
		.ifIs[StatementPattern]

	val deprecatedDobjVarName: NodeSearch[StatementPattern, String] = sp => {
		val (s, p, o) = splitTriple(sp)
		if(meta.isNextVersionOf == p.getValue && s.isAnonymous && !s.hasValue && !o.isAnonymous)
			Some(o.getName)
		else
			None
	}

	val isLatestDobjVersionFilter: TopNodeSearch[ExcludeDeprecatedPattern] = takeNode
		.ifIs[Filter]
		.thenAlsoSearch(
			notExistsSingleStatementPattern.thenSearch(deprecatedDobjVarName)
		)
		.thenGet{
			case (filter, varName) => new ExcludeDeprecatedPattern(filter, varName)
		}
		.recursive

	def dataObjFetchPatternSearch(filterOpt: Option[ExcludeDeprecatedPattern]): TopNodeSearch[DataObjectFetchPattern] = takeNode
		.ifIs[StatementPattern]
		.thenSearch(sp => {
			val (s, p, o) = splitTriple(sp)
			val dobjVarIsSame = filterOpt.forall(_.dobjVarName == s.getName)
			if(meta.hasObjectSpec == p.getValue && !s.isAnonymous && !o.isAnonymous && dobjVarIsSame)
				Some(new DataObjectFetchPattern(sp, filterOpt.map(_.filter)))
			else
				None
		})
		.recursive

	val search: TopNodeSearch[DataObjectFetchPattern] = node => {
		val filterOpt = isLatestDobjVersionFilter(node)
		val startNode = filterOpt.fold(node)(_.filter)
		dataObjFetchPatternSearch(filterOpt)(startNode)
	}

	def inUnion[O](inner: TopNodeSearch[O]): NodeSearch[Union, O] =
		u => inner(u.getLeftArg).orElse(inner(u.getRightArg))

	val startTimePattern: TopNodeSearch[DataStartTimePattern] = {

		val startTimeL3: TopNodeSearch[TimePatternVars] = takeNode.ifIs[StatementPattern].thenSearch{sp =>
			val (s, p, o) = splitTriple(sp)
			if(meta.hasStartTime == p.getValue && !s.isAnonymous && !o.isAnonymous)
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
				if meta.wasAcquiredBy == pl.getValue && meta.prov.startedAtTime == pr.getValue && ol.getName == sr.getName &&
					!sl.isAnonymous && !or.isAnonymous
			) yield
				new TimePatternVars(sl.getName, or.getName)
		}

		takeNode.ifIs[Union].thenSearch(union => {
			for(
				l2 <- inUnion(startTimeL2)(union);
				l3 <- inUnion(startTimeL3)(union) if l3 == l2
			) yield {
				//TODO Needed only to suppress a false unused warning, which is due to a compiler bug, to be fixed in Scala 2.13
				assert(l3 != null)
				new DataStartTimePattern(union, l2)
			}
		})
	}
}

class ExcludeDeprecatedPattern(val filter: Filter, val dobjVarName: String)
case class TimePatternVars(val dobjVarName: String, val timeVarName: String)
class DataStartTimePattern(val union: Union, val vars: TimePatternVars)

class DataObjectFetchPattern(val objSpec: StatementPattern, val noDeprecated: Option[Filter]){
	def fuse(): Unit = {
		noDeprecated.foreach{filter =>
			filter.replaceWith(filter.getArg)
		}
		objSpec.replaceWith(new DataObjectFetch(objSpec, noDeprecated.isDefined))
	}
}
