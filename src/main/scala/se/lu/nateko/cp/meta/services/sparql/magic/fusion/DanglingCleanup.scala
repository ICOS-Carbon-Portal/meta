package se.lu.nateko.cp.meta.services.sparql.magic.fusion
import org.eclipse.rdf4j.query.algebra.StatementPattern
import org.eclipse.rdf4j.query.algebra.SingletonSet
import org.eclipse.rdf4j.query.algebra.QueryModelNode

object DanglingCleanup{
	import PatternFinder._

	def clean(query: QueryModelNode): Unit = {
		while(cleanOnce(query)){}
	}

	private def cleanOnce(query: QueryModelNode): Boolean = danglingSearch(query) match{
		case None => false
		case Some(dangling) =>
			val userOpt = danglingUserSearch(dangling)(query)
			if(userOpt.isDefined) false
			else {
				dangling.sp.replaceWith(new SingletonSet)
				true
			}
	}

	val danglingSearch: TopNodeSearch[Dangling] = takeNode
		.ifIs[StatementPattern]
		.thenAlsoSearch{sp =>
			val objVar = sp.getObjectVar
			if(objVar.isAnonymous && !objVar.hasValue) Some(objVar.getName)
			else None
		}
		.thenGet{
			case (sp, varName) => new Dangling(varName, sp)
		}
		.recursive

	def danglingUserSearch(d: Dangling): TopNodeSearch[Unit] = takeNode
		.ifIs[StatementPattern]
		.filter{sp =>
			val subjVar = sp.getSubjectVar
			subjVar.isAnonymous && !subjVar.hasValue && subjVar.getName == d.anonVarName
		}
		.thenGet(_ => ())
		.recursive

	class Dangling(val anonVarName: String, val sp: StatementPattern)
}