package se.lu.nateko.cp.meta.rdfstore

import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.api.UriId

class RemoteRdfStoreTests extends AnyFunSpec:

	describe("StoreLikeValueFactory"):

		it("makes IRIs whose UriId is the last path segment, as with the LMDB store"):
			val station = RemoteRdfStore.StoreLikeValueFactory.createIRI("http://meta.icos-cp.eu/resources/", "stations/OS_11SS")
			assert(station.stringValue === "http://meta.icos-cp.eu/resources/stations/OS_11SS")
			assert(UriId(station).urlSafeString === "OS_11SS")
