package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra._
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch
import se.lu.nateko.cp.meta.services.sparql.index.DataObjectFetch.{Filter => _, _}

trait DobjPattern{
	def exprs: Seq[TupleExpr]
//	def dobjVar: String
	def removeExprs(): Unit = exprs.foreach(_.replaceWith(new SingletonSet))
}

class DobjPropPattern(val exprs: Seq[TupleExpr], val propVar: String) extends DobjPattern

class DobjCategPattern[T <: AnyRef](val propPatt: DobjPropPattern, bsa: BindingSetAssignment, val vals: Seq[T]) extends DobjPattern{
	override def exprs = propPatt.exprs :+ bsa
}

// trait SingleExprDobjPattern extends DobjPattern{
// 	def expr: TupleExpr
// 	override def exprs = Seq(expr)
// }

//class ObjSpecPattern(val expr: StatementPattern, val dobjVar: String, val specVar: String) extends SingleExprDobjPattern

class ExcludeDeprecatedPattern(val expr: Filter, val dobjVar: String) extends DobjPattern{
	override def exprs = Seq(expr)
	override def removeExprs(): Unit = expr.getParentNode.replaceChildNode(expr, expr.getArg)
}

// class TwoStepPropPathPattern(val path: StatementPatternSearch.TwoStepPropPath) extends DobjPattern{
// 	def dobjVar: String =  path.subjVariable
// 	override def exprs = Seq(path.step1, path.step2)
// }

class DataObjectFetchPattern(
	categPatterns: Map[CategProp[_], DobjCategPattern[_]],
	propPatterns: Map[ContProp[_], DobjPropPattern],
//	spec: ObjSpecPattern,
	noDeprecated: Option[ExcludeDeprecatedPattern]
	// dataStart: Option[TempCoveragePattern],
	// dataEnd: Option[TempCoveragePattern],
	// submStart: Option[TwoStepPropPathPattern],
	// submEnd: Option[TwoStepPropPathPattern],
	// station: Option[TwoStepPropPathPattern]
){

	val allPatterns: Seq[DobjPattern] = categPatterns.values.toSeq ++ propPatterns.values ++ noDeprecated

	def fuse(): Unit = if(!allPatterns.isEmpty){

		val deepest = allPatterns.maxBy(p => p.exprs.map(nodeDepth).max)
		val fetch = new DataObjectFetch(
			selections = Seq(
				selection(Spec, Nil)
			),
			filtering = new Filtering(Nil, true, Nil),
			sort = None,
			offset = 0
		)
		val varNames: Map[Property[_], String] = categPatterns.mapValues(_.propPatt.propVar) ++ propPatterns.mapValues(_.propVar)

		val fetchExpr = new DataObjectFetchNode(fetch, varNames)
		// 	deepest.dobjVar,
		// 	spec.map(_.specVar),
		// 	dataStart.map(_.timeVar),
		// 	dataEnd.map(_.timeVar),
		// 	submStart.map(_.path.objVariable),
		// 	submEnd.map(_.path.objVariable),
		// 	station.map(_.path.objVariable),
		// 	noDeprecated.isDefined
		// )
		val deepestExpr = deepest.exprs.maxBy(nodeDepth)
		deepest.exprs.filter(_ ne deepestExpr).foreach(_.replaceWith(new SingletonSet))
		deepestExpr.replaceWith(fetchExpr)

		for(patt <- allPatterns if patt ne deepest) patt.removeExprs()
	}

}
