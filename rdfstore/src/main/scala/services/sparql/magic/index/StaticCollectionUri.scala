package se.lu.nateko.cp.meta.services.sparql.magic.index

import scala.language.unsafeNulls

import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import se.lu.nateko.cp.meta.core.data.collectionPathPrefix

import java.net.URI

object StaticCollectionUri:
	def unapply(uri: URI): Option[Sha256Sum] =
		val path = uri.getRawPath.stripPrefix("/")
		if !path.startsWith(collectionPathPrefix) then None
		else Sha256Sum.fromString(path.stripPrefix(collectionPathPrefix)).toOption
