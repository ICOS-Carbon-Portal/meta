package se.lu.nateko.cp.meta.core

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.net.URI

package object data{

	type OptionalOneOrSeq[T] = Option[Either[T, Seq[T]]]

	def staticObjLandingPage(hash: Sha256Sum)(implicit envri: EnvriConfig) = new URI(
		s"$objectPrefix${hash.id}"
	)

	def staticObjAccessUrl(hash: Sha256Sum)(implicit envri: EnvriConfig) = new URI(
		s"https://${envri.dataHost}/$objectPathPrefix${hash.id}"
	)

	def staticCollLandingPage(hash: Sha256Sum)(implicit envri: EnvriConfig) = new URI(
		s"$collectionPrefix${hash.id}"
	)

	def staticCollAccessUrl(landingPage: URI)(implicit envri: EnvriConfig) = new URI(
		s"https://${envri.dataHost}${landingPage.getPath}"
	)

	def objectPrefix(implicit envri: EnvriConfig): String = s"${envri.dataItemPrefix}$objectPathPrefix"
	def collectionPrefix(implicit envri: EnvriConfig): String = s"${envri.dataItemPrefix}$collectionPathPrefix"

	val objectPathPrefix = "objects/"
	val collectionPathPrefix = "collections/"
}
