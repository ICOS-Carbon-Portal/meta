package se.lu.nateko.cp.meta.upload.formcomponents

import FormTypeRadio._

class FormTypeRadio(elemId: String, cb: FormType => Unit) extends Radio[FormType](elemId, cb, formTypeParser, formTypeSerializer) {
	def formType: FormType = value.getOrElse(defaultType)
}

object FormTypeRadio {
	sealed trait FormType
	case object Document extends FormType
	case object Data extends FormType
	case object Collection extends FormType

	val formTypeParser: String => Option[FormType] = _ match {
		case "data" => Some(Data)
		case "collection" => Some(Collection)
		case "document" => Some(Document)
		case _ => None
	}
	val defaultType: FormType = Document

	val formTypeSerializer: FormType => String = _ match {
		case Data => "data"
		case Collection => "collection"
		case Document => "document"
	}
}
