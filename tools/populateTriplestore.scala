import se.lu.nateko.cp.meta.ConfigLoader
import se.lu.nateko.cp.meta.cli.TriplestorePopulator

/*
	Populate external triplestore using RDF Graph Update protocol
	NOTE: Requires large amounts of RAM, which means you most likely need:
		export SBT_OPTS="-Xmx12G -Xss2M"
*/
@main def populateTriplestore(args: String*): Unit =
	val config = ConfigLoader.default
	TriplestorePopulator.run(config, args.headOption)
