package se.lu.nateko.cp.meta.services.upload

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.api.RdfLens.{DobjConn, DocConn, MetaConn}
import se.lu.nateko.cp.meta.core.data.{CitableItem, References, StaticObject}
import se.lu.nateko.cp.meta.utils.Validated

trait StaticObjectReferenceProvider:
	def getItemCitationInfo(item: CitableItem): References
	def getCitationInfo(item: StaticObject, specConn: MetaConn)(using Envri, DocConn | DobjConn): Validated[References]
