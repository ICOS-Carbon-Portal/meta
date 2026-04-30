package se.lu.nateko.cp.meta.upload.formcomponents

import ModeRadio.*

class ModeRadio(elemId: String, cb: Mode => Unit) extends Radio[Mode](elemId, cb, ModeParser, ModeSerializer) {
	def isNewItemOrVersion: Boolean = value match {
		case Some(Update) => false
		case _ => true
	}
}

object ModeRadio {
	sealed trait Mode
	case object NewItem extends Mode
	case object Update extends Mode
	case object NewVersion extends Mode

	val ModeParser: String => Option[Mode] = _ match {
		case "update" => Some(Update)
		case "new-version" => Some(NewVersion)
		case "new-item" => Some(NewItem)
		case _ => None
	}

	val ModeSerializer: Mode => String = _ match {
		case Update => "update"
		case NewVersion => "new-version"
		case NewItem => "new-item"
	}
}
