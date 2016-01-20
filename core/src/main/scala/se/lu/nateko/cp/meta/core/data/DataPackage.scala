package se.lu.nateko.cp.meta.core.data

import java.net.URI

case class DataPackageSpec(format: URI, encoding: URI, dataLevel: Int)
case class PackageSubmission(submittingOrg: URI)
case class DataPackage(spec: DataPackageSpec, submission: PackageSubmission)
