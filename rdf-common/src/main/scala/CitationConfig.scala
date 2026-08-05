package se.lu.nateko.cp.meta

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.doi.core.{DoiEndpointConfig, DoiMemberConfig}
import se.lu.nateko.cp.meta.core.CommonJsonSupport
import se.lu.nateko.cp.meta.core.MetaCoreConfig.given
import spray.json.*

import java.net.URI

case class CitationConfig(style: String, eagerWarmUp: Boolean, timeoutSec: Int, doi: DoiConfig)
case class DoiConfig(restEndpoint: URI, envries: Map[Envri, DoiMemberConfig]) extends DoiEndpointConfig

/**
 * `CitationConfig`/`DoiConfig` used to live inline in `CpmetaConfig.scala`. They were pulled out
 * early (ahead of task 14, which moves the rest of `CpmetaConfig.scala`) because the citation
 * stack (task 11) needs them to compile once it lives in `rdfCommon`, and unlike the rest of
 * `CpmetaConfig`, these two case classes have no dependency on the metaflow/instance-server
 * configuration that still lives in `rdfStore`.
 */
object CitationConfigJsonProtocol extends CommonJsonSupport:
	import DefaultJsonProtocol.*

	given RootJsonFormat[DoiMemberConfig] = jsonFormat3(DoiMemberConfig.apply)
	given RootJsonFormat[DoiConfig] = jsonFormat2(DoiConfig.apply)
	given RootJsonFormat[CitationConfig] = jsonFormat4(CitationConfig.apply)
