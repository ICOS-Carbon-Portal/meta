package se.lu.nateko.cp.meta

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.doi.core.{DoiEndpointConfig, DoiMemberConfig}
import se.lu.nateko.cp.meta.core.CommonJsonSupport
import se.lu.nateko.cp.meta.core.MetaCoreConfig.given
import spray.json.*

import java.net.URI

case class CitationConfig(doi: DoiConfig)
case class DoiConfig(restEndpoint: URI, envries: Map[Envri, DoiMemberConfig]) extends DoiEndpointConfig

/**
 * `CitationConfig` contains only the DOI settings shared by meta's DOI service and rdfStore's
 * citation cache. Citation rendering policy is owned by rdfStore.
 */
object CitationConfigJsonProtocol extends CommonJsonSupport:
	import DefaultJsonProtocol.*

	given RootJsonFormat[DoiMemberConfig] = jsonFormat3(DoiMemberConfig.apply)
	given RootJsonFormat[DoiConfig] = jsonFormat2(DoiConfig.apply)
	given RootJsonFormat[CitationConfig] = jsonFormat1(CitationConfig.apply)
