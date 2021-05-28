package se.lu.nateko.cp.meta.upload

import se.lu.nateko.cp.meta.UploadDto
import se.lu.nateko.cp.meta.upload.formcomponents.ItemTypeRadio.ItemType

sealed trait PubSubEvent

final case object ModeChanged extends PubSubEvent
final case class ItemTypeSelected(itemType: ItemType) extends PubSubEvent
final case class LevelSelected(level: Int) extends PubSubEvent
final case class ObjSpecSelected(spec: ObjSpec) extends PubSubEvent

final case class GotStationsList(stations: IndexedSeq[Station]) extends PubSubEvent
final case class GotUploadDto(dto: UploadDto) extends PubSubEvent
final case class GotAgentList(agents: IndexedSeq[Agent]) extends PubSubEvent
final case class GotOrganizationList(people: IndexedSeq[Organization]) extends PubSubEvent

final case object FormInputUpdated extends PubSubEvent
