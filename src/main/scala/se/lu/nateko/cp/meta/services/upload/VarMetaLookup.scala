package se.lu.nateko.cp.meta.services.upload

import scala.util.matching.Regex
import java.net.URI
import se.lu.nateko.cp.meta.core.data.ValueType
import se.lu.nateko.cp.meta.core.data.VarMeta

class DatasetVariable(val title: String, val valueType: ValueType, val valueFormat: URI, val isRegex: Boolean, val isOptional: Boolean){
	def plain: Option[VarMeta] = if(!isRegex) Some(VarMeta(title, valueType, valueFormat, None)) else None
}

class VarMetaLookup(varDefs: Seq[DatasetVariable]){

	val plainMandatory = varDefs.filterNot(_.isOptional).flatMap(_.plain)

	private val plainLookup: Map[String, VarMeta] = varDefs.flatMap(_.plain).map(vm => vm.label -> vm).toMap

	private val regexes = varDefs.filter(_.isRegex).sortBy(_.isOptional).map{
		dv => new Regex(dv.title) -> dv
	}

	def lookup(varName: String): Option[VarMeta] = plainLookup.get(varName).orElse(
		regexes.collectFirst{
			case (reg, dv) if reg.matches(varName) => VarMeta(varName, dv.valueType, dv.valueFormat, None)
		}
	)

}
