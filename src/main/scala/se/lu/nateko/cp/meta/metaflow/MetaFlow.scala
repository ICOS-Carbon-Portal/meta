package se.lu.nateko.cp.meta.metaflow

import icos.AtcMetaSource

class MetaFlow(val atcSourceOpt: Option[AtcMetaSource], val cancel: () => Unit)
