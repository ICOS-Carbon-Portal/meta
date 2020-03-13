package se.lu.nateko.cp.meta.services.sparql.magic.fusion
import org.eclipse.rdf4j.query.algebra.BindingSetAssignment
import org.eclipse.rdf4j.model.IRI
import scala.jdk.CollectionConverters.IterableHasAsScala

object BindingSetAssignmentSearch{
	import PatternFinder._

	def byVarName(varName: String): TopNodeSearch[SingleVarIriBindSetAssignment] = takeNode
		.ifIs[BindingSetAssignment]
		.thenSearch{bsa =>
			bsa.getBindingNames.toArray match{

				case Array(`varName`) =>

					val values = bsa.getBindingSets().asScala.map(_.getValue(varName)).collect{
						case iri: IRI => iri
					}.toIndexedSeq

					if(values.isEmpty) None
					else Some(new SingleVarIriBindSetAssignment(bsa, values))

				case _ => None
			}
		}

	class SingleVarIriBindSetAssignment(val expr: BindingSetAssignment, val values: Seq[IRI])
}
