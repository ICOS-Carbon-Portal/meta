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
 * by both meta's `DoiService` and rdfStore's `CitationClient`. It is not an application config
 * *section* -- the `cpmeta.citations` section wrapping it is defined separately by each
 * application (`CitationConfig` in meta's CpmetaConfig.scala and in rdfStore's
 * CitationStoreConfig.scala), so either can extend its own citations section independently.
 */
case class DoiConfig(restEndpoint: URI, envries: Map[Envri, DoiMemberConfig]) extends DoiEndpointConfig

object DoiConfigJsonProtocol extends CommonJsonSupport:
	import DefaultJsonProtocol.*

	given RootJsonFormat[DoiMemberConfig] = jsonFormat3(DoiMemberConfig.apply)
	given RootJsonFormat[DoiConfig] = jsonFormat2(DoiConfig.apply)
