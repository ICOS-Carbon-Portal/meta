package se.lu.nateko.cp.meta.rdfstore

import org.scalatest.funspec.AnyFunSpec

class CliOptionsTest extends AnyFunSpec:

	describe("rdfStore command-line options"):
		it("does not restore from the RDF log by default"):
			assert(CliOptions.parse(Nil) === Right(CliOptions(restoreFromRdfLog = false)))

		it("enables restoration when the flag is present"):
			assert(
				CliOptions.parse(Seq(CliOptions.RestoreFlag)) === Right(CliOptions(restoreFromRdfLog = true))
			)

		it("rejects unknown arguments"):
			assert(CliOptions.parse(Seq("--rebuild-everything")).isLeft)

end CliOptionsTest
