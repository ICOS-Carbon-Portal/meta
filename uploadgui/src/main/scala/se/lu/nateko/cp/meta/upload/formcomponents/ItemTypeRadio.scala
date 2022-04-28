package se.lu.nateko.cp.meta.upload.formcomponents

import ItemTypeRadio.*

class ItemTypeRadio(elemId: String, cb: ItemType => Unit) extends Radio[ItemType](elemId, cb, itemTypeParser, itemTypeSerializer)

object ItemTypeRadio {
	sealed trait ItemType
	case object Document extends ItemType
	case object Data extends ItemType
	case object Collection extends ItemType

	val itemTypeParser: String => Option[ItemType] = _ match {
		case "data" => Some(Data)
		case "collection" => Some(Collection)
		case "document" => Some(Document)
		case _ => None
	}

	val defaultType: ItemType = Document

	val itemTypeSerializer: ItemType => String = _ match {
		case Data => "data"
		case Collection => "collection"
		case Document => "document"
	}
}
