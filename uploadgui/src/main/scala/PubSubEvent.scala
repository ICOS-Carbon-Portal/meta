package se.lu.nateko.cp.meta.upload

import se.lu.nateko.cp.meta.UploadDto
import se.lu.nateko.cp.meta.upload.formcomponents.ItemTypeRadio.ItemType

sealed trait PubSubEvent

case object ModeChanged extends PubSubEvent
final case class ItemTypeSelected(itemType: ItemType) extends PubSubEvent
final case class LevelSelected(level: Int) extends PubSubEvent
final case class ObjSpecSelected(spec: ObjSpec) extends PubSubEvent

final case class GotStationsList(stations: IndexedSeq[Station]) extends PubSubEvent
final case class GotVariableList(variables: IndexedSeq[DatasetVar]) extends PubSubEvent
final case class GotUploadDto(dto: UploadDto) extends PubSubEvent

case object FormInputUpdated extends PubSubEvent
