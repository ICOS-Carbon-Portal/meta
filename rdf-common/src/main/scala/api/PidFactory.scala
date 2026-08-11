package se.lu.nateko.cp.meta.api

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum

/**
 * Pure PID *naming*: prefix lookup per ENVRI, suffix derivation from an object hash, and
 * handle.net URL construction. No I/O.
 *
 * Kept in rdf-common, and deliberately separate from `HandleNetClient`, because both
 * applications need to name PIDs - `rdfStore`'s citation provider and the shared
 * `StaticObjectReader` - while only `meta` ever *mints* them against the handle.net API.
 * Splitting this out is what lets the client itself (with its akka-http and client-certificate
 * TLS machinery) live in `meta` alone.
 */
class PidFactory(baseUrl: String, prefix: Map[Envri, String]){
	def prefix(using envri: Envri): String = prefix.getOrElse(
		envri,
		throw new Exception(s"No PID prefix for ENVRI $envri in the config")
	)
	def getPid(suffix: String)(using Envri) = s"${prefix}/$suffix"
	def getSuffix(hash: Sha256Sum): String = hash.id
	def getPid(hash: Sha256Sum)(using Envri): String = getPid(getSuffix(hash))
	def pidUrlStr(suffix: String)(using Envri) = s"${baseUrl}api/handles/${getPid(suffix)}"
}
