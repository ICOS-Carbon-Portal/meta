package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra._

trait DobjPattern{
	def dobjVar: String
	def expr: TupleExpr
	/** Default implementation, to be overridden for some special cases */
	def removeExpr(): Unit = expr.replaceWith(new SingletonSet)
}

class ObjSpecPattern(val expr: StatementPattern, val dobjVar: String, val specVar: String) extends DobjPattern

class TempCoveragePattern(val expr: Union, val dobjVar: String, val timeVar: String) extends DobjPattern

class ExcludeDeprecatedPattern(val expr: Filter, val dobjVar: String) extends DobjPattern{
	override def removeExpr(): Unit = expr.getParentNode.replaceChildNode(expr, expr.getArg)
}

case class TimePatternVars(val dobjVar: String, val timeVar: String)

class DataObjectFetchPattern(
	spec: Option[ObjSpecPattern],
	noDeprecated: Option[ExcludeDeprecatedPattern],
	dataStart: Option[TempCoveragePattern],
	dataStop: Option[TempCoveragePattern]
){

	val allPatterns: Seq[DobjPattern] = spec.toSeq ++ noDeprecated ++ dataStart ++ dataStop

	def fuse(): Unit = if(!allPatterns.isEmpty){

		val deepest = allPatterns.maxBy(p => depth(p.expr))

		val fetchExpr = new DataObjectFetch(
			deepest.dobjVar,
			spec.map(_.specVar),
			dataStart.map(_.timeVar),
			dataStop.map(_.timeVar),
			noDeprecated.isDefined
		)
		deepest.expr.replaceWith(fetchExpr)

		for(patt <- allPatterns if patt ne deepest) patt.removeExpr()
	}

	def depth(expr: TupleExpr): Int = {
		var res = -1
		var ancestor: QueryModelNode = expr
		while(ancestor != null){
			ancestor = ancestor.getParentNode
			res += 1
		}
		res
	}
}
