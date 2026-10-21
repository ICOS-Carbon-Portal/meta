package se.lu.nateko.cp.meta.test

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.MetaDb
import se.lu.nateko.cp.meta.api.UriId

class MetaDbTests extends AnyFunSpec:

	describe("StoreLikeValueFactory"):

		it("makes IRIs whose UriId is the last path segment, as with the LMDB store"):
			val station = MetaDb.StoreLikeValueFactory.createIRI("http://meta.icos-cp.eu/resources/", "stations/OS_11SS")
			assert(station.stringValue === "http://meta.icos-cp.eu/resources/stations/OS_11SS")
			assert(UriId(station).urlSafeString === "OS_11SS")
