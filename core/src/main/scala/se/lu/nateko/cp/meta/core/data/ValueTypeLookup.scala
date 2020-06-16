package se.lu.nateko.cp.meta.core.data

import scala.util.matching.Regex

class DatasetVariable[IRI](val title: String, val valueType: IRI, val isRegex: Boolean, val isOptional: Boolean)

class ValueTypeLookup[IRI](varDefs: Seq[DatasetVariable[IRI]]){

	private val plainLookup = varDefs.collect{
		case dv if !dv.isRegex => dv.title -> dv.valueType
	}.toMap

	private val regexes = varDefs.filter(_.isRegex).sortBy(_.isOptional).map{
		dv => new Regex(dv.title) -> dv.valueType
	}

	def lookup(varName: String): Option[IRI] = plainLookup.get(varName).orElse(
		regexes.collectFirst{
			case (reg, valueType) if reg.matches(varName) => valueType
		}
	)
}

