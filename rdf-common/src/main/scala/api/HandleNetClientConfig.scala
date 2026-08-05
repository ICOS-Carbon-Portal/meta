package se.lu.nateko.cp.meta.api

import eu.icoscp.envri.Envri

case class HandleNetClientConfig(
	prefix: Map[Envri, String],
	baseUrl: String,
	serverCertPemFilePath: Option[String],
	clientCertPemFilePath: String,
	clientPrivKeyPKCS8FilePath: String,
	dryRun: Boolean
)
