package se.lu.nateko.cp.meta.test.services.external

import scala.language.unsafeNulls

import eu.icoscp.envri.Envri
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.*
import se.lu.nateko.cp.meta.ExternalProviderConfig

import java.net.URI
import java.time.Instant

object ExternalTestObjects:

	val envriConfigs: EnvriConfigs = MetaCoreConfig.default.envriConfigs

	val hash: Sha256Sum = Sha256Sum.fromBase64Url("old_vJN69j6rRPKxTbJZckEa").get

	def provider(host: String) = ExternalProviderConfig(host = host, label = s"label of $host")

	def docObject(
		accessUrl: Option[URI],
		doi: Option[String] = None,
		references: References = References.empty,
		pid: Option[String] = None
	) = DocObject(
		hash = hash,
		accessUrl = accessUrl,
		pid = pid,
		doi = doi,
		fileName = "test.txt",
		size = None,
		description = None,
		submission = DataSubmission(
			submitter = Organization(UriResource(URI("https://example.org/org"), None, Nil), "Org", None, None, None),
			start = Instant.EPOCH,
			stop = Some(Instant.EPOCH)
		),
		previousVersion = None,
		nextVersion = None,
		latestVersion = Left(URI("https://example.org/latest")),
		parentCollections = Nil,
		references = references
	)

	def localAccessUrl(using EnvriConfig): URI = staticObjAccessUrl(hash)

	def sitesConf: EnvriConfig = envriConfigs(Envri.SITES)
