package se.lu.nateko.cp.meta

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.doi.core.{DoiEndpointConfig, DoiMemberConfig}
import se.lu.nateko.cp.meta.core.CommonJsonSupport
import se.lu.nateko.cp.meta.core.MetaCoreConfig.given
import spray.json.*

import java.net.URI

/**
 * The DataCite endpoint and per-envri member credentials. This type stays in `rdf-common`
 * because shared code takes it as a parameter: `DoiClientFactory` builds the DOI clients used
 * by both meta's `DoiService` and rdfStore's `CitationClient`. Its defaults live at the shared
 * `cpmeta.citations.doi` path; each application still owns its wrapper type and may add
 * service-specific citation settings outside that shared object.
 */
case class DoiConfig(restEndpoint: URI, envries: Map[Envri, DoiMemberConfig]) extends DoiEndpointConfig

object DoiConfigJsonProtocol extends CommonJsonSupport:
	import DefaultJsonProtocol.*

	given RootJsonFormat[DoiMemberConfig] = jsonFormat3(DoiMemberConfig.apply)
	given RootJsonFormat[DoiConfig] = jsonFormat2(DoiConfig.apply)
