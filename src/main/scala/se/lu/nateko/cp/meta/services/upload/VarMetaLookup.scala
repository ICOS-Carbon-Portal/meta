package se.lu.nateko.cp.meta.services.upload

import scala.util.matching.Regex
import java.net.URI
import se.lu.nateko.cp.meta.core.data.ValueType
import se.lu.nateko.cp.meta.core.data.VarMeta
import se.lu.nateko.cp.meta.core.data.UriResource
import se.lu.nateko.cp.meta.core.data.InstrumentDeployment

class DatasetVariable(
	val model: UriResource,
	val title: String,
	val valueType: ValueType,
	val valueFormat: Option[URI],
	val isRegex: Boolean,
	val isOptional: Boolean,
	val instrumentDeployment: Option[InstrumentDeployment]
){
	def plain: Option[VarMeta] = if(!isRegex) Some(VarMeta(model, title, valueType, valueFormat, None, instrumentDeployment)) else None
}

class VarMetaLookup(varDefs: Seq[DatasetVariable]){

	val plainMandatory = varDefs.filterNot(_.isOptional).flatMap(_.plain)

	private val plainLookup: Map[String, VarMeta] = varDefs.flatMap(_.plain).map(vm => vm.label -> vm).toMap

	private val regexes = varDefs.filter(_.isRegex).sortBy(_.isOptional).map{
		dv => new Regex(dv.title) -> dv
	}

	def lookup(varName: String): Option[VarMeta] = plainLookup.get(varName).orElse(
		regexes.collectFirst{
			case (reg, dv) if reg.matches(varName) => VarMeta(dv.model, varName, dv.valueType, dv.valueFormat, None, dv.instrumentDeployment)
		}
	)

}
