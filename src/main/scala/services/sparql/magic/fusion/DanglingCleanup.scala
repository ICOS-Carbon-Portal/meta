package se.lu.nateko.cp.meta.services.sparql.magic.fusion

import scala.language.unsafeNulls

import org.eclipse.rdf4j.query.algebra.helpers.AbstractQueryModelVisitor
import org.eclipse.rdf4j.query.algebra.{QueryModelNode, SingletonSet, StatementPattern}

object DanglingCleanup{

	def clean(query: QueryModelNode): Unit = {
		val collector = new StatPattCollector
		collector.meetNode(query)
		for((v, sp) <- collector.dangling if !collector.used.contains(v)){
			sp.replaceWith(new SingletonSet)
		}
	}

	private class StatPattCollector extends AbstractQueryModelVisitor[Exception]{
		import scala.collection.mutable.{Set, Buffer}
		val dangling = Buffer.empty[(AnonVar,StatementPattern)]
		val used = Set.empty[AnonVar]

		override def meetNode(node: QueryModelNode): Unit = {
			node match{
				case sp: StatementPattern =>
					if(!sp.getObjectVar.hasValue) QVar(sp.getObjectVar) match{
						case a: AnonVar => dangling += a -> sp
						case _ =>
					}
					QVar(sp.getSubjectVar) match{
						case a: AnonVar => used += a
						case _ =>
					}
				case _ =>
			}
			node.visitChildren(this)
		}
	}

}
