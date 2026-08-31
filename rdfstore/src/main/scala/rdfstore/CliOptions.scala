package se.lu.nateko.cp.meta.rdfstore

/**
 * Command-line options of the rdfStore service.
 *
 * RDF-log replay is never implicit: an operator has to ask for it explicitly with
 * `--restore`, also when the RDF storage is new and empty. Such a run replays and
 * exits; it does not go on to serve queries.
 */
final case class CliOptions(restoreFromRdfLog: Boolean)

object CliOptions:

	val RestoreFlag = "--restore"

	def usage: String = s"""Usage: rdfstore [$RestoreFlag]
		|
		|  $RestoreFlag  replay the configured RDF logs into the store, then exit,
		|             instead of serving the store as it is on disk""".stripMargin

	def parse(args: Seq[String]): Either[String, CliOptions] =
		val unknown = args.filterNot(_ == RestoreFlag)
		if unknown.nonEmpty
		then Left(s"Unknown command-line argument(s): ${unknown.mkString(", ")}")
		else Right(CliOptions(restoreFromRdfLog = args.contains(RestoreFlag)))

end CliOptions
