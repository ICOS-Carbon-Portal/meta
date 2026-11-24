package se.lu.nateko.cp.meta.services.linkeddata

import scala.language.unsafeNulls

import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path.{Empty, Segment, Slash}
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.{collectionPathPrefix, objectPathPrefix}

import java.net.{URI => JavaUri}

object Hash {
	def unapply(arg: String): Option[Sha256Sum] = Sha256Sum.fromString(arg).toOption

	object Object extends HashExtractor(objectPathPrefix.stripSuffix("/"))
	object Collection extends HashExtractor(collectionPathPrefix.stripSuffix("/"))

	abstract class HashExtractor(segment: String) {
		def unapply(arg: Uri.Path): Option[Sha256Sum] = arg match {
			case Slash(Segment(`segment`, Slash(Segment(Hash(hash), Empty)))) => Some(hash)
			case _ => None
		}
		def unapply(uri: JavaUri): Option[Sha256Sum] =
			val path = uri.getRawPath.stripPrefix("/")
			if !path.startsWith(segment) then None
			else Hash.unapply(path.stripPrefix(segment).stripPrefix("/"))
	}
}
