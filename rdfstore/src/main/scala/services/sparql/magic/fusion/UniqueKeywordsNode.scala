package se.lu.nateko.cp.meta.services.sparql.magic.fusion
import org.eclipse.rdf4j.query.algebra.{AbstractQueryModelNode, TupleExpr, QueryModelVisitor, QueryModelNode}

// This is a special node which is only used to carry a wrapped DataObjectFetchNode.
// Execution is entirely managed by us in CpEvaluationStrategyFactory, hence we can ignore all visitors.
final case class UniqueKeywordsNode(bindingName: String, inner: DataObjectFetchNode) extends AbstractQueryModelNode with TupleExpr {
	override def getSignature(): String = s"UniqueKeywordsNode($inner)"
	override def visit[X <: Exception](visitor: QueryModelVisitor[X]): Unit = {}

	override def getBindingNames() = unexpected("getBindingNames")
	override def getAssuredBindingNames() = unexpected("getAssuredBindingNames")
	override def clone() = unexpected("clone")
	override def visitChildren[X <: Exception](visitor: QueryModelVisitor[X]): Unit = unexpected("visitChildren")
	override def replaceChildNode(current: QueryModelNode, replacement: QueryModelNode): Unit = unexpected("replaceChildNode")

	private def unexpected(methodName: String) =
		throw new Exception(s"Unexpected call to UniqueKeywordsNode.${methodName}")
}
