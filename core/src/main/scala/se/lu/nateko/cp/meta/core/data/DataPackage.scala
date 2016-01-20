package se.lu.nateko.cp.meta.core.data

import java.net.URI
import se.lu.nateko.cp.meta.core.crypto.Sha256Sum
import java.time.Instant

case class UriResource(uri: URI, label: Option[String])

case class DataPackageSpec(format: URI, encoding: URI, dataLevel: Int)

case class PackageSubmission(submittingOrg: UriResource, start: Instant, stop: Option[Instant])
case class PackageProduction(producer: UriResource)

case class DataPackage(
	hash: Sha256Sum,
	production: PackageProduction,
	submission: PackageSubmission,
	spec: DataPackageSpec
)

