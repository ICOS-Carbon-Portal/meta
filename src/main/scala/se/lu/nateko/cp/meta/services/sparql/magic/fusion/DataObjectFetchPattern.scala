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

class TwoStepPropPathPattern(val path: StatementPatternSearch.TwoStepPropPath) extends DobjPattern{
	def dobjVar: String =  path.subjVariable
	def expr = path.step2
	override def removeExpr(): Unit = {
		path.step1.replaceWith(new SingletonSet)
		path.step2.replaceWith(new SingletonSet)
	}
}

case class TimePatternVars(val dobjVar: String, val timeVar: String)

class DataObjectFetchPattern(
	spec: Option[ObjSpecPattern],
	noDeprecated: Option[ExcludeDeprecatedPattern],
	dataStart: Option[TempCoveragePattern],
	dataEnd: Option[TempCoveragePattern],
	submStart: Option[TwoStepPropPathPattern],
	submEnd: Option[TwoStepPropPathPattern],
	station: Option[TwoStepPropPathPattern]
){

	val allPatterns: Seq[DobjPattern] = spec.toSeq ++ noDeprecated ++ dataStart ++ dataEnd ++ submStart ++ submEnd ++ station

	def fuse(): Unit = if(!allPatterns.isEmpty){

		val deepest = allPatterns.maxBy(p => nodeDepth(p.expr))

		val fetchExpr = new DataObjectFetchNode(
			deepest.dobjVar,
			spec.map(_.specVar),
			dataStart.map(_.timeVar),
			dataEnd.map(_.timeVar),
			submStart.map(_.path.objVariable),
			submEnd.map(_.path.objVariable),
			station.map(_.path.objVariable),
			noDeprecated.isDefined
		)
		deepest.expr.replaceWith(fetchExpr)

		for(patt <- allPatterns if patt ne deepest) patt.removeExpr()
	}

}
