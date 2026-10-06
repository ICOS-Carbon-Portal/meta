package se.lu.nateko.cp.meta.api

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

class PidFactory(baseUrl: String, prefixes: Map[Envri, String]){
	def prefix(using envri: Envri): String = prefixes.getOrElse(
		envri,
		throw new Exception(s"No PID prefix for ENVRI $envri in the config")
	)
	def getPid(suffix: String)(using Envri) = s"${prefix}/$suffix"
	def getSuffix(hash: Sha256Sum): String = hash.id
	def getPid(hash: Sha256Sum)(using Envri): String = getPid(getSuffix(hash))
	def pidUrlStr(suffix: String)(using Envri) = s"${baseUrl}api/handles/${getPid(suffix)}"
}
