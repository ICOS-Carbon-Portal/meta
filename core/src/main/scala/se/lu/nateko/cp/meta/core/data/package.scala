package se.lu.nateko.cp.meta.core.data

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.net.URI

def staticObjLandingPage(hash: Sha256Sum)(using EnvriConfig) = new URI(
	s"$objectPrefix${hash.id}"
)

def staticObjAccessUrl(hash: Sha256Sum)(using envri: EnvriConfig) = new URI(
	s"https://${envri.dataHost}/$objectPathPrefix${hash.id}"
)

def staticCollLandingPage(hash: Sha256Sum)(using EnvriConfig) = new URI(
	s"$collectionPrefix${hash.id}"
)

def staticCollAccessUrl(landingPage: URI)(using envri: EnvriConfig) = new URI(
	s"https://${envri.dataHost}${landingPage.getPath}"
)

def envriConf(using configs: EnvriConfigs, envri: Envri): EnvriConfig =
	configs.getOrElse(envri, throw new Exception(s"No config found for ENVRI $envri"))

def objectPrefix(using envri: EnvriConfig): String = s"${envri.dataItemPrefix}$objectPathPrefix"
def collectionPrefix(using envri: EnvriConfig): String = s"${envri.dataItemPrefix}$collectionPathPrefix"

val objectPathPrefix = "objects/"
val collectionPathPrefix = "collections/"

type OptionalOneOrSeq[T] = Option[Either[T, Seq[T]]]
type OneOrSeq[T] = Either[T, Seq[T]]

enum DatasetType derives CanEqual:
	case StationTimeSeries, SpatioTemporal
