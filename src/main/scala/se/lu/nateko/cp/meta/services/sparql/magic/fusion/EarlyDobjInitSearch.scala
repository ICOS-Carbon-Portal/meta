package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import org.eclipse.rdf4j.query.algebra.TupleExpr
import scala.jdk.CollectionConverters.CollectionHasAsScala
import PatternFinder._

object EarlyDobjInitSearch{

	val search: TopNodeSearch[Unit] = takeNode
		.ifIs[DataObjectFetchNode]
		.recursive
		.thenSearch{dofn =>
			val earlyVarNames = earlierEvaluatedNodesInSameQuery(dofn).collect{
				case expr: TupleExpr => expr.getBindingNames.asScala
			}.flatten
			if(earlyVarNames.contains(dofn.dobjVarName)) Some(())
			else None
		}

	def hasEarlyDobjInit(query: TupleExpr): Boolean = search(query).isDefined

}
