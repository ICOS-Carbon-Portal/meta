package se.lu.nateko.cp.meta.core

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.net.URI

package object data{

	type OptionalOneOrSeq[T] = Option[Either[T, Seq[T]]]

	def staticObjLandingPage(hash: Sha256Sum)(implicit envri: EnvriConfig) = new URI(
		s"$staticObjPrefix${hash.id}"
	)

	def staticObjPrefix(implicit envri: EnvriConfig): String = s"${envri.metaPrefix}objects/"

}
