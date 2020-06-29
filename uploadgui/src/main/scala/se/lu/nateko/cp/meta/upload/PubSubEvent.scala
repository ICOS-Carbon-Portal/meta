package se.lu.nateko.cp.meta.upload

import se.lu.nateko.cp.meta.upload.formcomponents.ItemTypeRadio.ItemType

sealed trait PubSubEvent

final case object FormInputUpdated extends PubSubEvent

final case object NewItemMode extends PubSubEvent
final case object UpdateMetaMode extends PubSubEvent

final case class GotStationsList(stations: IndexedSeq[Station]) extends PubSubEvent
final case class ItemTypeSelected(itemType: ItemType) extends PubSubEvent
