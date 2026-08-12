package se.lu.nateko.cp.meta

import scala.language.unsafeNulls

import se.lu.nateko.cp.meta.api.HandleNetClientConfig
import se.lu.nateko.cp.meta.core.MetaCoreConfig
import spray.json.*

import java.net.URI

// Split out of the former monolithic CpmetaConfig.scala (docs/rdf-common-split/15-split-config.md):
// value types that both `meta` (from its own `cpmeta.*` view) and `rdfStore` (from `rdfStore.*`
// and its own narrower `cpmeta.*` view) parse. Keeping one definition here, rather than two
// structurally-identical copies, is what lets both loaders share a single JSON format instead of
// having to be kept in sync by hand.

case class DbServer(host: String, port: Int)
case class DbCredentials(db: String, user: String, password: String)
case class RdflogConfig(server: DbServer, credentials: DbCredentials)

case class DataObjectInstServerDefinition(label: String, format: URI, replayLogFrom: Option[Int] = None)

case class DataObjectInstServersConfig(
	commonReadContexts: Seq[URI],
	uriPrefix: URI,
	definitions: Seq[DataObjectInstServerDefinition]
)

object SharedConfigJsonProtocol extends se.lu.nateko.cp.meta.core.CommonJsonSupport:
	import DefaultJsonProtocol.*
	import MetaCoreConfig.given

	given RootJsonFormat[DbServer] = jsonFormat2(DbServer.apply)
	given RootJsonFormat[DbCredentials] = jsonFormat3(DbCredentials.apply)
	given RootJsonFormat[RdflogConfig] = jsonFormat2(RdflogConfig.apply)
	given RootJsonFormat[DataObjectInstServerDefinition] = jsonFormat3(DataObjectInstServerDefinition.apply)
	given RootJsonFormat[DataObjectInstServersConfig] = jsonFormat3(DataObjectInstServersConfig.apply)
	given RootJsonFormat[HandleNetClientConfig] = jsonFormat6(HandleNetClientConfig.apply)

end SharedConfigJsonProtocol
